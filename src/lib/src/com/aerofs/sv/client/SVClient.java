package com.aerofs.sv.client;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Param;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.RecentExceptions;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.rocklog.Defect.Priority;
import com.aerofs.lib.rocklog.RockLog;
import com.aerofs.proto.Sv.PBSVCall;
import com.aerofs.proto.Sv.PBSVCall.Type;
import com.aerofs.proto.Sv.PBSVDefect;
import com.aerofs.proto.Sv.PBSVDefect.Builder;
import com.aerofs.proto.Sv.PBSVEmail;
import com.aerofs.proto.Sv.PBSVEvent;
import com.aerofs.proto.Sv.PBSVGzippedLog;
import com.aerofs.proto.Sv.PBSVHeader;
import com.aerofs.sv.common.EmailCategory;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.MissingResourceException;
import java.util.Stack;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.aerofs.lib.FileUtil.deleteOrOnExit;
import static com.aerofs.lib.Param.FILE_BUF_SIZE;
import static com.aerofs.lib.ThreadUtil.startDaemonThread;
import static com.aerofs.lib.Util.crc32;
import static com.aerofs.lib.Util.deleteOldHeapDumps;
import static com.aerofs.lib.Util.e;
import static com.aerofs.lib.Util.join;
import static com.aerofs.lib.cfg.Cfg.absRTRoot;
import static com.aerofs.lib.cfg.CfgDatabase.Key.ROOT;

public final class SVClient
{
    private static final Logger l = Loggers.getLogger(SVClient.class);

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
    private static final SVRPCClient client = new SVRPCClient("https://sv.aerofs.com:443/sv_beta/sv");

    private static SVRPCClient getRpcClient()
    {
        return client;
    }

    private static final RecentExceptions recentExceptions = RecentExceptions.getInstance();

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
        l.info("send email " + from + " -> " + to);

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

    public static void sendCoreDatabaseSync()
    {
        l.debug("send dump");
        try {
            doLogSendDefect(
                    true,
                    "core db",
                    null,
                    newHeader(),
                    absRTRoot(),
                    null,
                    false,
                    true,
                    false,
                    false);
        } catch (Throwable e) {
            l.warn("dump core db:" + Util.e(e));
        }
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
                    l.warn("send sv event: " + Util.e(e, IOException.class));
                }
            }
        }, SVClient.class.getName() + ".event");

        eventSender.start();
    }

    public static void sendEventSync(PBSVEvent.Type type, @Nullable String desc)
    {
        if (L.get().isStaging()) {
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
        } catch (Throwable e) {
            l.warn("send sv event: " + Util.e(e, IOException.class));
        }
    }

    //-------------------------------------------------------------------------
    //
    // SEND DEFECT (WITH LOGS)
    //
    //-------------------------------------------------------------------------

    public static void logSendDefectAsync(boolean isAutoBug, String desc)
    {
        logSendDefectAsync(isAutoBug, desc, null);
    }

    /*
     * @param cause may be null if stack trace is not needed
     */
    public static void logSendDefectAsync(final boolean isAutoBug, final String desc, @Nullable final Throwable cause)
    {
        final PBSVHeader header = newHeader(); // make header here so we can get correct event time

        startDaemonThread(SVClient.class.getName() + ".defect", new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    doLogSendDefect(
                            isAutoBug,
                            desc,
                            cause,
                            header,
                            absRTRoot(),
                            null,
                            true,
                            false,
                            true,
                            false);
                } catch (Throwable e) {
                    l.warn("send defect: " + Util.e(e));
                }
            }
        });
    }

    /**
     * @param secret the string that should be hidden from the log files
     */
    public static void logSendDefectSync(boolean isAutoBug, String desc, @Nullable Throwable cause, @Nullable String secret, boolean sendFileNames)
            throws IOException, AbstractExWirable
    {
        doLogSendDefect(
                isAutoBug,
                desc,
                cause,
                newHeader(),
                absRTRoot(),
                secret,
                true,
                false,
                true,
                sendFileNames);
    }

    public static void logSendDefectSyncIgnoreErrors(boolean isAutoBug, String context, Throwable cause)
    {
        try {
            logSendDefectSync(isAutoBug, context, cause, null, false);
        } catch (Throwable e) {
            l.error("send defect: " + Util.e(e));
        }
    }

    public static void logSendDefectSyncNoLogsIgnoreErrors(boolean isAutoBug, String context, Throwable cause)
    {
        try {
            doLogSendDefect(
                    isAutoBug,
                    context,
                    cause,
                    newHeader(),
                    absRTRoot(),
                    null,
                    false,
                    false,
                    false,
                    false);
        } catch (Throwable e) {
            l.error("send defect: " + Util.e(e));
        }
    }

    public static void logSendDefectSyncNoCfgIgnoreErrors(boolean isAutoBug, String context,
            Throwable cause, UserID user, String rtRoot)
    {
        try {
            doLogSendDefect(
                    isAutoBug,
                    context,
                    cause,
                    newHeader(user, null, rtRoot),
                    rtRoot,
                    null,
                    true,
                    false,
                    false,
                    false);
        } catch (Throwable e) {
            l.error("send defect: " + Util.e(e));
        }
    }

    //-------------------------------------------------------------------------
    //
    // THE MOTHER OF THEM ALL: doLogSendDefect(11-arg version)
    //
    //-------------------------------------------------------------------------

    /**
     * @param cause may be null if no exception is available
     *
     * this method doesn't reference Cfg if it's not inited. exit the program
     * if the error is OutOfMemory
     */
    private static void doLogSendDefect(
            boolean isAutoBug,
            String desc,
            @Nullable Throwable cause,
            PBSVHeader header,
            String rtRoot,
            @Nullable String secret,
            final boolean sendLogs,
            final boolean sendDB,
            final boolean sendHeapDumps,
            final boolean sendUnobfuscatedFileMapping)
            throws IOException, AbstractExWirable
    {
        l.debug("build defect");

        if (cause == null) cause = new Exception(desc); // FIXME (AG): bogus
        String stackTrace = Exceptions.getStackTraceAsString(cause);

        if (L.get().isStaging()) {
            l.warn("##### DEFECT #####\n" + desc + "\n" + Util.e(cause));
            l.warn("(sv defect sending disabled on staging.)");
            return;
        }

        // If we have any LinkageError (NoClassDefFoundError or UnsatisfiedLinkError) or
        // MissingResourceException, this probably indicates that our stripped-down version of
        // OpenJDK is missing something. Send a different RockLog defect to make sure we catch it.
        if (cause instanceof LinkageError || cause instanceof MissingResourceException) {
            RockLog.newDefect("system.classnotfound").setException(cause)
                    .setPriority(Priority.Fatal).send();
        } else {
            // Note: we can't pick a good defect name here since we don't know who created this
            // defect, so we use the generic "svdefect" string. See doc in newDefect() for more
            // info about defect names.
            RockLog.newDefect("svdefect").setMessage(desc).setException(cause).send();
        }

        // always send non-automatic defects and database requests
        boolean ignoreDefect = isAutoBug && recentExceptions.isRecent(cause) && !sendDB;
        l.error((ignoreDefect ? "repeating last" : "sending") + " defect: " + desc + ": " + Util.e(cause));
        if (ignoreDefect) return;

        StringBuilder sbDesc = createDefectDescription(desc, secret);

        Map<Key, String> cfgDB = Cfg.inited() ? Cfg.dumpDb() : Collections.<Key, String>emptyMap();
        PBSVDefect pbDefect = createPBDefect(isAutoBug, header, cfgDB, rtRoot, stackTrace, sbDesc);

        File defectFilesZip = compressDefectLogs(rtRoot, sendLogs, sendDB, sendHeapDumps,
                sendUnobfuscatedFileMapping);

        PBSVCall call = PBSVCall
                .newBuilder()
                .setType(Type.DEFECT)
                .setHeader(header)
                .setDefect(pbDefect)
                .build();

        l.debug("send defect");

        try {
            getRpcClient().doRPC(call, defectFilesZip);
        } finally {
            if (defectFilesZip != null) {
                deleteOrOnExit(defectFilesZip);
            }
        }

        l.debug("complete send defect");

        //
        // clean up state locally
        //

        recentExceptions.add(cause);

        if (Cfg.inited() && sendHeapDumps) deleteOldHeapDumps();

        // FIXME (AG): really? I'm pretty sure we won't be able to do any of this no?
        if (cause instanceof OutOfMemoryError) ExitCode.OUT_OF_MEMORY.exit();
    }

    private static StringBuilder createDefectDescription(String desc, String secret)
    {
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
        return sbDesc;
    }

    private static File compressDefectLogs(String rtRoot, final boolean sendLogs,
            final boolean sendDB, final boolean sendHeapDumps, boolean sendUnobfuscatedFileMapping)
    {
        File defectFilesZip = null;
        if (Cfg.inited() && Cfg.useArchive() && (sendLogs || sendDB || sendHeapDumps || sendUnobfuscatedFileMapping)) {
            try {
                // add log files
                File[] files = new File(rtRoot).listFiles(
                        new FilenameFilter() {
                            @Override
                            public boolean accept(File arg0, String arg1)
                            {
                                // Note: the core database consists of three files:
                                // db, db-wal, and db-shm.
                                return (sendLogs && arg1.endsWith(Param.LOG_FILE_EXT))
                                        ||
                                        (sendDB && (arg1.startsWith(Param.OBF_CORE_DATABASE) ||
                                                            arg1.endsWith("wal")        ||
                                                            arg1.endsWith("shm")))
                                        ||
                                        (sendHeapDumps && arg1.endsWith(Param.HPROF_FILE_EXT));
                            }
                        });

                if (files == null) {
                    l.error("rtroot not found");
                    files = new File[0];
                }

                if (sendUnobfuscatedFileMapping) {
                    // Send base64encoded(utf8 encoded <filename>)> crc32(<filename>) mapping.
                    File nameMap = createNameMapFile();
                    if (nameMap != null) {
                        files = Arrays.copyOf(files, files.length + 1);
                        files[files.length - 1] = nameMap;
                    }
                }

                l.debug("compressing " + files.length + " logs/db/hprof files");

                OutputStream os = null;
                try {
                    defectFilesZip = File.createTempFile("$$$", "zip");
                    os = new FileOutputStream(defectFilesZip);
                    compress(files, os);
                } finally {
                    if (os != null) os.close();
                }
            } catch (Throwable e) {
                l.error("compress defect logs, ignore: " + Util.e(e));
            }
        }
        return defectFilesZip;
    }

    private static PBSVDefect createPBDefect(boolean isAutoBug, PBSVHeader header,
            Map<Key, String> cfgDB, String rtRoot, String stackTrace, StringBuilder sbDesc)
    {
        String contactEmail = L.get().isMultiuser() ? cfgDB.get(Key.MULTIUSER_CONTACT_EMAIL) :
                header.getUser();

        Builder bdDefect = PBSVDefect
                .newBuilder()
                .setAutomatic(isAutoBug)
                .setDescription(sbDesc.toString())
                .setStacktrace(stackTrace)
                .setContactEmail(contactEmail);

        try {
            bdDefect.addJavaEnvName("os full name");
            bdDefect.addJavaEnvValue(OSUtil.get().getFullOSName());
        } catch (Throwable t) {
            // ignored
        }

        if (!OSUtil.isWindows()) {
            // "uname -a" result
            OutArg<String> uname = new OutArg<String>();
            uname.set("n/a");
            try {
                SystemUtil.execForeground(uname, "uname", "-a");
            } catch (Throwable t) {
                // ignored
            }
            bdDefect.addJavaEnvName("uname -a");
            bdDefect.addJavaEnvValue(uname.get());

            // "file /bin/ls" result
            OutArg<String> fileRes = new OutArg<String>();
            fileRes.set("n/a");
            try {
                SystemUtil.execForeground(fileRes, "file", "-L", "/bin/ls");
            } catch (Throwable t) {
                //ignored
            }
            bdDefect.addJavaEnvName("file bin/ls");
            bdDefect.addJavaEnvValue(fileRes.get());

            // "df" result
            OutArg<String> df = new OutArg<String>();
            df.set("n/a");
            try {
                SystemUtil.execForeground(df, "df");
            } catch (Throwable t) {
                // ignored
            }
            bdDefect.addJavaEnvName("df");
            bdDefect.addJavaEnvValue(df.get());
        }

        if (rtRoot != null) {
            // XXX (AG): well, I hope and pray that since we have an rtroot Cfg is actually in a good state
            // filesystem type
            if (Cfg.inited()) {
                bdDefect.addJavaEnvName("fs");
                try {
                    OutArg<Boolean> remote = new OutArg<Boolean>();
                    String fs = OSUtil.get().getFileSystemType(Cfg.db().getNullable(ROOT), remote);
                    bdDefect.addJavaEnvValue(fs + ", remote " + remote.get());
                } catch (Throwable t) {
                    bdDefect.addJavaEnvValue(t.toString());
                }
            }

            // free space on the rtroot partition
            bdDefect.addJavaEnvName("free");
            try {
                String freeSpace = listFreeSpaceOnPartition(rtRoot);
                l.debug("free space:" + freeSpace);
                bdDefect.addJavaEnvValue(freeSpace);
            } catch (Throwable t) {
                bdDefect.addJavaEnvValue("n/a");
            }

            // files and their sizes in rtroot
            bdDefect.addJavaEnvName("files");
            try {
                String fileSizes = listTopLevelContents(rtRoot);
                l.debug("file sizes:" + fileSizes);
                bdDefect.addJavaEnvValue(fileSizes);
            } catch (Throwable t) {
                bdDefect.addJavaEnvValue("n/a");
            }
        }

        // java env
        for (Entry<Object, Object> en : System.getProperties().entrySet()) {
            bdDefect.addJavaEnvName(en.getKey().toString());
            bdDefect.addJavaEnvValue(en.getValue().toString());
        }

        //
        // Cfg values
        //

        StringBuilder sbCfgDB = new StringBuilder();
        for (Entry<Key, String> en : cfgDB.entrySet()) {
            sbCfgDB.append(en.getKey());
            sbCfgDB.append(": ");
            sbCfgDB.append(en.getValue());
            sbCfgDB.append('\n');
        }
        bdDefect.setCfgDb(sbCfgDB.toString());

        return bdDefect.build();
    }

    //-------------------------------------------------------------------------
    //
    // PRIVATE UTILITY METHODS
    //
    //-------------------------------------------------------------------------

    /**
     * List free space for the partition in which this path resides
     */
    private static String listFreeSpaceOnPartition(String path)
            throws IOException
    {
        File dir = new File(path);
        if (!dir.exists()) {
            throw new IOException(path + " path for rtroot does not exist");
        }

        return "[F:" + dir.getFreeSpace() + " U:" + dir.getUsableSpace() + "]/" + dir.getTotalSpace() + "]";
    }

    /**
     * List files (along with their sizes) in the path
     * FIXME (AG): what happens if the user has put AeroFS in a bad place (i.e. under cache, etc)
     */
    private static String listTopLevelContents(String path)
            throws IOException
    {
        StringBuilder bd = new StringBuilder();

        File dir = new File(path);
        if (!dir.isDirectory()) {
            throw new IOException(path + " is not a directory");
        }

        File[] children = dir.listFiles();
        if (children == null || children.length == 0) {
            bd.append("empty");
            return bd.toString();
        }

        for (File f : children) {
            // skip the AeroFS folder (could be arbitrarily deep), but log it
            if (f.isDirectory() && f.getName().equalsIgnoreCase("AeroFS")) {
                bd.append(f.getName()).append(": ").append("IGNORED");
            } else {
                bd.append(f.getName()).append(": ").append(BaseUtil.getDirSize(f));
            }

            bd.append('\n');
        }

        return bd.toString();
    }

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
                    l.warn("compress " + f + ": " + Util.e(e));
                }
            }
        } finally {
            zipos.close();
        }
    }

    private static PBSVHeader newHeader()
    {
        return Cfg.inited() ? newHeader(Cfg.user(), Cfg.did(), absRTRoot())
                            : newHeader(UserID.UNKNOWN, null, "unknown");
    }

    // FIXME (AG): I can get rid of this by creating a static block with a header initializer
    private static PBSVHeader newHeader(UserID user, @Nullable DID did, String rtRoot)
    {
        if (did == null) did = new DID(UniqueID.ZERO);

        return PBSVHeader
                .newBuilder()
                .setTime(System.currentTimeMillis())
                .setUser(user.getString())
                .setDeviceId(did.toPB())
                .setVersion(Cfg.ver())
                .setAppRoot(AppRoot.abs())
                .setRtRoot(rtRoot)
                .build();
    }

    private static File createNameMapFile()
    {
        try {
            File f = File.createTempFile("name", "map");

            BufferedWriter bw = new BufferedWriter(new FileWriter(f));
            writeFileNames(bw);
            bw.close();

            f.deleteOnExit();
            return f;
        } catch (IOException e) {
            l.warn("create temp file failed: " + e(e));
            return null;
        }
    }

    /**
     * DFS on the AeroFS folder and write every file name with its crc32 version.
     */
    private static void writeFileNames(BufferedWriter bw) throws IOException
    {
        Stack<String> stack = new Stack<String>();
        stack.push(Cfg.absRootAnchor());

        while (!stack.isEmpty()) {
            String currentPath = stack.pop();
            File currentFile = new File(currentPath);

            // Send the base64 encoded version of filename to avoid the case of \n char in the name.
            String encodedName = Base64.encodeBytes(currentFile.getName().getBytes("UTF-8"));
            bw.write(crc32(currentFile.getName())+ " " + encodedName + "\n");

            String[] children = currentFile.list();
            if (children == null) {
                // currentFile is of type file.
                continue;
            }

            for (String child : children) {
                String childPath = join(currentPath, child);
                stack.push(childPath);
            }
        }
    }
}
