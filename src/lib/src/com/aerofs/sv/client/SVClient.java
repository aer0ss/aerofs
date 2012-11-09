package com.aerofs.sv.client;

import com.aerofs.l.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.C;
import com.aerofs.lib.ExitCode;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.AbstractExWirable;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.rocklog.RockLog;
import com.aerofs.proto.Sv.PBSVCall;
import com.aerofs.proto.Sv.PBSVCall.Type;
import com.aerofs.proto.Sv.PBSVDefect;
import com.aerofs.proto.Sv.PBSVEmail;
import com.aerofs.proto.Sv.PBSVEvent;
import com.aerofs.proto.Sv.PBSVGzippedLog;
import com.aerofs.proto.Sv.PBSVHeader;
import com.aerofs.sv.common.EmailCategory;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.aerofs.lib.C.LAST_SENT_DEFECT;
import static com.aerofs.lib.FileUtil.deleteOrOnExit;
import static com.aerofs.lib.Param.FILE_BUF_SIZE;
import static com.aerofs.lib.Util.deleteOldHeapDumps;
import static com.aerofs.lib.Util.join;
import static com.aerofs.lib.Util.startDaemonThread;
import static com.aerofs.lib.cfg.Cfg.absRTRoot;
import static com.aerofs.lib.cfg.CfgDatabase.Key.ROOT;

public final class SVClient
{
    private static final Logger l = Util.l(SVClient.class);

    //-------------------------------------------------------------------------
    //
    // BACKEND CLIENT (HTTP COMMUNICATION)
    //
    //-------------------------------------------------------------------------

    //
    // NOTE: I do this so that I can test out the SVRPCClient separately from
    // the methods that do all the PB building, log-file zipping, etc. It also keeps
    // all reference to various hard-coded bits of AeroFS (Cfg, L, etc.) here
    // and out of the more utilitarian SVRPCClient
    //
    // This method of accessing SVRPCClient provides lazy, only-one evaluation
    // followed by uncontended access. It is the simplest way to provide:
    // 1) A testable SVRPCClient
    // 2) static methods inside SVClient itself
    //
    // See "Effective Java, 2nd Edition (Joshua Bloch) pg. 283
    //

    private static final class SVRPCClientHolder
    {
        private static final SVRPCClient client = new SVRPCClient("https://" + L.get().svHost() + ":" + L.get().svPort() + "/sv_beta/sv");
    }

    private static SVRPCClient getRpcClient()
    {
        return SVRPCClientHolder.client;
    }

    //-------------------------------------------------------------------------
    //
    // SEND EMAIL
    //
    //-------------------------------------------------------------------------

    public static void sendEmail(
            String from,
            String fromName,
            String to,
            @Nullable String replyTo,
            String subject,
            String textBody,
            @Nullable String htmlBody,
            boolean usingSendGrid,
            @Nullable EmailCategory category)
            throws IOException, AbstractExWirable
    {
        l.debug("send email " + from + " -> " + to);

        assert !usingSendGrid || category != null;

        PBSVEmail.Builder bdEmail = PBSVEmail
                .newBuilder()
                .setFrom(from)
                .setFromName(fromName)
                .setTo(to)
                .setSubject(subject)
                .setTextBody(textBody)
                .setUsingSendgrid(usingSendGrid);

        if (replyTo != null)  bdEmail.setReplyTo(replyTo);
        if (htmlBody != null) bdEmail.setHtmlBody(htmlBody);
        if (category != null) bdEmail.setCategory(category.name());

        PBSVCall call = PBSVCall
                .newBuilder()
                .setType(Type.EMAIL)
                .setEmail(bdEmail)
                .build();

        getRpcClient().doRPC(call, null);
    }

    //-------------------------------------------------------------------------
    //
    // SEND GZIPPED LOG
    //
    //-------------------------------------------------------------------------

    public static void sendGZippedLog(File gzippedLog)
            throws IOException, AbstractExWirable
    {
        l.debug("send log");

        PBSVCall call = PBSVCall
                .newBuilder()
                .setType(Type.GZIPPED_LOG)
                .setHeader(newHeader())
                .setGzippedLog(PBSVGzippedLog
                        .newBuilder()
                        .setName(gzippedLog.getName()))
                .build();

        getRpcClient().doRPC(call, gzippedLog);
    }

    //-------------------------------------------------------------------------
    //
    // SEND CORE DB
    //
    //-------------------------------------------------------------------------

    public static void sendCoreDatabaseAsync()
    {
        l.debug("send dump");

        startDaemonThread("send-defect", new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    doLogSendDefect(
                            true,
                            "core db",
                            null,
                            newHeader(),
                            Cfg.dumpDb(),
                            absRTRoot(),
                            null,
                            true,
                            false,
                            true,
                            false);
                } catch (Exception e) {
                    l.warn("can't dump err:", e);
                }
            }
        });
    }

    //-------------------------------------------------------------------------
    //
    // SEND EVENT
    //
    //-------------------------------------------------------------------------

    public static void sendEventAsync(PBSVEvent.Type type)
    {
        sendEventAsync(type, null);
    }

    /**
     * @param desc optional description of the event
     */
    public static void sendEventAsync(final PBSVEvent.Type type, @Nullable final String desc)
    {
        Thread eventSender = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try {
                    sendEventSync(type, desc);
                } catch (Throwable e) {
                    l.warn("can't archive logs err:", e);
                }
            }
        }, SVClient.class.getName() + ".event");

        eventSender.start();
    }

    public static void sendEventSync(PBSVEvent.Type type, @Nullable String desc)
    {
        if (Cfg.staging()) {
            l.debug("sv event sending disabled on staging");
            return;
        }

        l.debug("send event type:" + type);

        PBSVEvent.Builder bdEvent = PBSVEvent
                .newBuilder()
                .setType(type);

        if (desc != null) bdEvent.setDesc(desc);

        PBSVCall call = PBSVCall
                .newBuilder()
                .setType(Type.EVENT)
                .setHeader(newHeader())
                .setEvent(bdEvent)
                .build();

        try {
            getRpcClient().doRPC(call, null);
        } catch (Exception e) {
            l.warn("can't send event err:", e);
        }
    }

    //-------------------------------------------------------------------------
    //
    // SEND DEFECT (WITH LOGS)
    //
    //-------------------------------------------------------------------------

    public static void logSendDefectAsync(boolean automatic, String desc)
    {
        logSendDefectAsync(automatic, desc, null);
    }

    /*
     * @param cause may be null if stack trace is not needed
     */
    public static void logSendDefectAsync(final boolean automatic, final String desc, @Nullable final Throwable cause)
    {
        final PBSVHeader header = newHeader(); // make header here so we can get correct event time

        startDaemonThread(SVClient.class.getName() + ".defect", new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    doLogSendDefect(
                            automatic,
                            desc,
                            cause,
                            header,
                            Cfg.dumpDb(),
                            absRTRoot(),
                            null,
                            true,
                            true,
                            false,
                            true);
                } catch (Throwable e) {
                    l.warn("can't send defect err:", e);
                }
            }
        });
    }

    /**
     * @param secret the string that should be hidden from the log files
     */
    public static void logSendDefectSync(boolean automatic, String desc, @Nullable Throwable cause, @Nullable String secret)
            throws IOException, AbstractExWirable
    {
        doLogSendDefect(
                automatic,
                desc,
                cause,
                newHeader(),
                Cfg.dumpDb(),
                absRTRoot(),
                secret,
                true,
                true,
                false,
                true);
    }

    public static void logSendDefectSyncIgnoreErrors(boolean automatic, String context, Throwable cause)
    {
        try {
            logSendDefectSync(automatic, context, cause, null);
        } catch (Exception e) {
            l.error("can't send defect err:", e);
        }
    }

    public static void logSendDefectSyncNoLogsIgnoreErrors(boolean automatic, String context, Throwable cause)
    {
        try {
            doLogSendDefect(
                    automatic,
                    context,
                    cause,
                    newHeader(),
                    Cfg.dumpDb(),
                    absRTRoot(),
                    null,
                    false,
                    false,
                    false,
                    false);
        } catch (Exception e) {
            l.error("can't send defect err:", e);
        }
    }

    public static void logSendDefectSyncNoCfgIgnoreErrors(boolean automatic, String context, Throwable cause, String user, String rtRoot)
    {
        try {
            doLogSendDefect(
                    automatic,
                    context,
                    cause,
                    newHeader(user, null, rtRoot),
                    Collections.<Key, String>emptyMap(),
                    rtRoot,
                    null,
                    true,
                    true,
                    false,
                    false);
        } catch (Exception e) {
            l.error("can't send defect err:", e);
        }
    }

    //-------------------------------------------------------------------------
    //
    // THE MOTHER OF THEM ALL: doLogSendDefect(11-arg version)
    //
    //-------------------------------------------------------------------------

    /**
     * @param verbose false to collect as less data as possible
     * @param cause may be null if no exception is available
     *
     * this method doesn't reference Cfg if it's not inited. exit the program
     * if the error is OutOfMemory
     */
    private static void doLogSendDefect(
            boolean automatic,
            String desc,
            @Nullable Throwable cause,
            PBSVHeader header,
            @Nonnull Map<Key, String> cfgDB,
            String rtRoot,
            @Nullable String secret,
            boolean verbose,
            final boolean sendLogs,
            final boolean sendDB,
            final boolean sendHeapDumps)
            throws IOException, AbstractExWirable
    {
        l.debug("build defect");

        if (cause == null) cause = new Exception(desc); // FIXME (AG): bogus
        String stackTrace = Util.stackTrace2string(cause);

        //
        // basic defect info
        //

        if (Cfg.staging()) {
            l.warn("##### DEFECT #####\n" + desc + "\n" + Util.e(cause));
            l.warn("(sv defect sending disabled on staging.)");
            return;
        }

        // always send non-automatic defects
        boolean isLastSent = automatic && isLastSentDefect(cause.getMessage(), stackTrace);
        l.error((isLastSent ? "repeating last" : "sending") + " defect: " + desc + ": " + Util.e(cause));
        if (isLastSent) return;

        // Send the defect to RockLog
        if (header.getUser().endsWith("@aerofs.com")) {
            RockLog.newDefect("svclient.test").setMsg(desc).setEx(cause).send();
        }

        StringBuilder sbDesc = new StringBuilder();

        if (desc != null) {
            sbDesc.append(desc);
            sbDesc.append(": ");
        }

        if (secret != null) {
            sbDesc.append("\n");
            sbDesc.append(secret);
            sbDesc.append("\n");
        }

        PBSVDefect.Builder bdDefect = PBSVDefect
                .newBuilder()
                .setAutomatic(automatic)
                .setDescription(sbDesc.toString())
                .setStacktrace(stackTrace);

        File defectFilesZip = File.createTempFile("$$$", "zip");

        if (verbose) {

            //
            // environment and other local information
            //

            StringBuilder sbCfgDB = new StringBuilder();
            for (Entry<Key, String> en : cfgDB.entrySet()) {
                sbCfgDB.append(en.getKey() + ": " + en.getValue());
                sbCfgDB.append('\n');
            }
            bdDefect.setCfgDb(sbCfgDB.toString());

            // "uname -a" result
            OutArg<String> uname = new OutArg<String>();
            uname.set("n/a");
            try {
                Util.execForeground(uname, "uname", "-a");
            } catch (IOException e) {
                // ignored
            }
            bdDefect.addJavaEnvName("uname -a");
            bdDefect.addJavaEnvValue(uname.get());

            // "file /bin/ls" result
            OutArg<String> fileRes = new OutArg<String>();
            fileRes.set("n/a");
            try {
                Util.execForeground(fileRes, "file", "-L", "/bin/ls");
            } catch (IOException e) {
                //ignored
            }
            bdDefect.addJavaEnvName("file bin/ls");
            bdDefect.addJavaEnvValue(fileRes.get());

            // filesystem type
            if (Cfg.inited()) {
                bdDefect.addJavaEnvName("fs");
                try {
                    OutArg<Boolean> remote = new OutArg<Boolean>();
                    String fs = OSUtil.get().getFileSystemType(Cfg.db().getNullable(ROOT), remote);
                    bdDefect.addJavaEnvValue(fs + ", remote " + remote.get());
                } catch (IOException e) {
                    bdDefect.addJavaEnvValue(e.toString());
                }
            }

            try {
                bdDefect.addJavaEnvName("os full name");
                bdDefect.addJavaEnvValue(OSUtil.get().getFullOSName());
            } catch (Throwable t) {
                // ignored
            }

            // "df" result
            OutArg<String> df = new OutArg<String>();
            df.set("n/a");
            try {
                Util.execForeground(df, "df");
            } catch (Exception e) {
                // ignored
            }
            bdDefect.addJavaEnvName("df");
            bdDefect.addJavaEnvValue(df.get());

            // java env
            for (Entry<Object, Object> en : System.getProperties().entrySet()) {
                bdDefect.addJavaEnvName(en.getKey().toString());
                bdDefect.addJavaEnvValue(en.getValue().toString());
            }

            //
            // zip defect logs
            //

            // don't send defect log for SP or staging
            if (Cfg.inited() && Cfg.useArchive() && (sendLogs || sendDB || sendHeapDumps)) {
                try {
                    // add log files
                    File[] files = new File(rtRoot).listFiles(
                            new FilenameFilter() {
                                @Override
                                public boolean accept(File arg0, String arg1)
                                {
                                    // Note: the core database consists of three files:
                                    // db, db-wal, and db-shm.
                                    return (sendLogs && arg1.endsWith(C.LOG_FILE_EXT)) ||
                                            (sendDB && arg1.startsWith(C.CORE_DATABASE)) ||
                                            (sendHeapDumps && arg1.endsWith(C.HPROF_FILE_EXT));
                                }
                            });

                    if (files == null) {
                        l.error("rtroot not found");
                        files = new File[0];
                    }

                    l.debug("compressing " + files.length + " logs/db/hprof files");

                    OutputStream os = null;
                    try {
                        os = new FileOutputStream(defectFilesZip);
                        compress(files, os);
                    } finally {
                        if (os != null) os.close();
                    }
                } catch (IOException e) {
                    l.error("can't compress defect logs; send them as is err:", cause);
                }
            }
        }

        //
        // make the client call
        //

        PBSVCall call = PBSVCall
                .newBuilder()
                .setType(Type.DEFECT)
                .setHeader(header)
                .setDefect(bdDefect)
                .build();

        l.debug("send defect");

        try {
            getRpcClient().doRPC(call, defectFilesZip);
        } finally {
            deleteOrOnExit(defectFilesZip);
        }

        l.debug("complete send defect");

        //
        // clean up state locally
        //

        setLastSentDefect(cause.getMessage(), stackTrace);

        if (Cfg.inited()) {
            if (sendHeapDumps) {
                deleteOldHeapDumps();
            }
        }

        // FIXME (AG): really? I'm pretty sure we won't be able to do any of this no?
        if (cause instanceof OutOfMemoryError) ExitCode.OUT_OF_MEMORY.exit();
    }

    //-------------------------------------------------------------------------
    //
    // PRIVATE UTILITY METHODS
    //
    //-------------------------------------------------------------------------

    /**
     * Compress multiple files into a single {@link OutputStream}
     *
     * @param files individual files to compress
     * @param os {@code OutputStream} to which to compress the files
     * @throws IOException
     */
    public static void compress(File[] files, OutputStream os)
        throws IOException
    {
        ZipOutputStream zipos = new ZipOutputStream(os);
        try {
            byte[] bs = new byte[FILE_BUF_SIZE];
            for (File f : files) {
                try {
                    ZipEntry ze = new ZipEntry(f.getName());
                    zipos.putNextEntry(ze);

                    InputStream is = null;
                    try {
                        is = new FileInputStream(f);
                        int read;
                        while ((read = is.read(bs)) != -1) {
                            zipos.write(bs, 0, read);
                        }
                    } finally {
                        if (is != null) is.close();
                    }
                } catch (IOException e) {
                    l.warn("fail compress err: ", e);
                }
            }
        } finally {
            zipos.close();
        }
    }

    private static boolean isLastSentDefect(String message, String stack)
            throws IOException
    {
        if (!Cfg.inited()) return false;

        final String lastSentDefectFile = join(absRTRoot(), LAST_SENT_DEFECT);

        try {
            DataInputStream is = new DataInputStream(new FileInputStream(lastSentDefectFile));
            try {
                return (message + stack).equals(is.readUTF());
            } finally {
                is.close();
            }
        } catch (FileNotFoundException e) {
            l.warn("file not found:" + lastSentDefectFile);
        } catch (Throwable e) {
            l.warn("fail lsd (bad trip) err:", e);
        }

        return false;
    }

    private static void setLastSentDefect(String message, String stack)
            throws IOException
    {
        if (!Cfg.inited()) return;

        final String lastSentDefectFile = join(absRTRoot(), LAST_SENT_DEFECT);

        try {
            DataOutputStream os = new DataOutputStream(new FileOutputStream(lastSentDefectFile));
            try {
                os.writeUTF(message + stack);
            } finally {
                os.close();
            }
        } catch (Throwable e) {
            l.warn("fail lsd (bad trip) err:", e);
        }
    }

    private static PBSVHeader newHeader()
    {
        assert Cfg.inited();

        return newHeader(Cfg.user(), Cfg.did(), absRTRoot());
    }

    // FIXME (AG): I can get rid of this by creating a static block with a header initializer
    private static PBSVHeader newHeader(String user, @Nullable DID did, String rtRoot)
    {
        if (did == null) did = new DID(UniqueID.ZERO);

        return PBSVHeader
                .newBuilder()
                .setTime(System.currentTimeMillis())
                .setUser(user)
                .setDeviceId(did.toPB())
                .setVersion(Cfg.ver())
                .setAppRoot(AppRoot.abs())
                .setRtRoot(rtRoot)
                .build();
    }
}
