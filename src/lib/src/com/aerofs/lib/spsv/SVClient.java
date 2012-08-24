package com.aerofs.lib.spsv;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
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
import java.net.Socket;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.aerofs.proto.Sv.PBSVEmail;
import org.apache.log4j.Logger;

import com.aerofs.l.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.C;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Param;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Sv.PBSVAnalytics;
import com.aerofs.proto.Sv.PBSVCall;
import com.aerofs.proto.Sv.PBSVCall.Type;
import com.aerofs.proto.Sv.PBSVDefect;
import com.aerofs.proto.Sv.PBSVEvent;
import com.aerofs.proto.Sv.PBSVGzippedLog;
import com.aerofs.proto.Sv.PBSVHeader;
import com.aerofs.proto.Sv.PBSVReply;
import com.google.common.collect.Maps;

/**
 * DO NOT use this class or its functions in daemon
 */
public class SVClient
{
    private static final Logger l = Util.l(SVClient.class);

    private static final String CONTENT_LENGTH_HEADER = "Content-Length: ";

    public static void archiveLogsAsync()
    {
        Thread thd = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try {
                    archiveLogs();
                } catch (Throwable e) {
                    l.warn("cannot archive logs: " + Util.e(e));
                }
            }
        }, SVClient.class.getName() + ".archive");

        // avoid quitting the program while sending logs
        // thd.setDaemon(false);
        thd.start();
    }

    // the original log files are deleted after archival
    private static void archiveLogs()
    {
        // don't archive logs for SP or staging
        if (!Cfg.useArchive()) {
            l.info("log archive disabled");
            return;
        }

        File[] fLogs = new File(Cfg.absRTRoot()).listFiles(
                new FilenameFilter() {
                    @Override
                    public boolean accept(File arg0, String arg1)
                    {
                        return arg1.contains(C.LOG_FILE_EXT + ".") &&
                            !arg1.endsWith(".gz");
                    }
                });

        if (fLogs == null) {
            l.error("rtRoot not found. stop");
            return;
        }

        for (File fLog : fLogs) {
            l.info("compressing " + fLog);
            try {
                // TODO the result may be too big to fit into memory
                FileOutputStream os =
                        new FileOutputStream(fLog.getPath() + ".gz");
                try {
                    compressSlowly(fLog, os);
                } finally {
                    os.close();
                }

                fLog.delete();

            } catch (Exception e) {
                l.error("cannot compress " + fLog + ": " + e);
            }
        }

        File[] fGZLogs = new File(Cfg.absRTRoot()).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File arg0, String arg1)
            {
                return arg1.contains(C.LOG_FILE_EXT + ".") &&
                    arg1.endsWith(".gz");
            }
        });

        for (File fGZLog : fGZLogs) {
            l.info("uploading " + fGZLog);
            try {
                PBSVCall call =
                        PBSVCall.newBuilder()
                                .setType(Type.GZIPPED_LOG)
                                .setHeader(newHeader())
                                .setGzippedLog(
                                        PBSVGzippedLog.newBuilder().setName(
                                                fGZLog.getName())).build();

                Socket s = send(call, fGZLog.length());
                try {
                        OutputStream os = s.getOutputStream();
                        InputStream isLog = new FileInputStream(fGZLog);
                        try {
                            byte[] buf = new byte[Param.FILE_BUF_SIZE];
                            while (true) {
                                int read = isLog.read(buf);
                                if (read == -1) break;
                                os.write(buf, 0, read);
                                os.flush();
                            }
                        } finally {
                            isLog.close();
                        }

                        recv(s);

                } finally {
                        if (!s.isClosed()) s.close();
                }

                // ignore errors
                if (!fGZLog.delete()) fGZLog.deleteOnExit();

            } catch (Exception e) {
                l.error("cannot upload " + fGZLog + ": ", e);
            }
        }
    }

    public static void sendEventAsync(PBSVEvent.Type type)
    {
        sendEventAsync(type, null);
    }

    /**
     * @param desc
     *            optional description of the event
     */
    public static void sendEventAsync(final PBSVEvent.Type type,
            final String desc)
    {
        Thread thd = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try {
                    sendEventSync(type, desc);
                } catch (Throwable e) {
                    l.warn("cannot archive logs: " + Util.e(e));
                }
            }
        }, SVClient.class.getName() + ".event");

        // avoid quitting the program while sending the event
        // thd.setDaemon(false);
        thd.start();
    }

    public static void sendEventSync(PBSVEvent.Type type, String desc)
    {
        if (Cfg.staging()) {
            l.info("sv event sending disabled on staging");
            return;
        }

        l.info("sending event " + type);

        PBSVEvent.Builder bdEvent = PBSVEvent.newBuilder().setType(type);
        if (desc != null) bdEvent.setDesc(desc);

        PBSVCall call = PBSVCall.newBuilder().setType(Type.EVENT)
                .setHeader(newHeader()).setEvent(bdEvent).build();

        try {
            rpc(call);
        } catch (Exception e) {
            l.info("cannot send event: " + e);
        }
    }

    private static PBSVHeader newHeader()
    {
        assert Cfg.inited();

        return newHeader(Cfg.user(), Cfg.did(), Cfg.absRTRoot());
    }

    private static PBSVHeader newHeader(String user, DID did, String rtRoot)
    {
        if (did == null) did = new DID(UniqueID.ZERO);

        return PBSVHeader.newBuilder().setTime(System.currentTimeMillis())
                .setUser(user).setDeviceId(did.toPB()).setVersion(Cfg.ver())
                .setAppRoot(AppRoot.abs())
                .setRtRoot(rtRoot).build();
    }

    public static void logSendDefectAsync(boolean automatic, String desc)
    {
        logSendDefectAsync(automatic, desc, new Exception(), null);
    }

    /*
     * @param e may be null if stack trace is not needed
     */
    public static void logSendDefectAsync(boolean automatic, String desc, Throwable e)
    {
        logSendDefectAsync(automatic, desc, e, null);
    }

    /**
     * send the defect and then archive logs. exit the program if the error
     * is OutOfMemory
     */
    public static void logSendDefectAsync(final boolean automatic,
            final String desc, final Throwable e, final String secret)
    {
        // create the header here so that we can get accurate creation time
        final PBSVHeader header = newHeader();

        Thread thd = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try {
                    doLogSendDefect(automatic, desc, e, header, getCfgDatabase(), Cfg.absRTRoot(),
                            secret, true, true, false);
                } catch (Throwable e) {
                    l.warn("can't send defect. ignored: " + Util.e(e));
                }
            }
        }, SVClient.class.getName() + ".defect");

        // avoid quitting the program while sending defects
        thd.setDaemon(false);
        thd.start();
    }

    public static void logSendDefectSyncNoCfg(boolean automatic,
            String context, Throwable e, String user, String rtRoot)
        throws IOException
    {
        try {
            doLogSendDefect(automatic, context, e, newHeader(user, null, rtRoot),
                    Collections.<Key, String>emptyMap(), rtRoot, null, true, true, false);
            // it may throw out of memory error
        } catch (Throwable e1) {
            throw new IOException(e1);
        }
    }

    public static void logSendDefectSync(boolean automatic, String desc,
            Throwable e) throws Exception
    {
        logSendDefectSync(automatic, desc, e, null);
    }

    public static void logSendDefectNoLogsIgnoreErrors(boolean automatic,
            String context, Throwable e)
    {
        try {
            doLogSendDefect(automatic,
                        context,
                        e,
                        newHeader(),
                        getCfgDatabase(),
                        Cfg.absRTRoot(),
                        null,
                        false,
                        false,
                        false);
        } catch (Throwable e2) {
            l.error("can't send defect:", e2);
        }
    }
    /**
     * @param secret the string that should be hidden from the log files
     */
    public static void logSendDefectSync(boolean automatic, String desc,
            @Nullable Throwable e, String secret) throws Exception
    {
        try {
            doLogSendDefect(automatic, desc, e, newHeader(), getCfgDatabase(), Cfg.absRTRoot(),
                    secret, true, true, false);
        } catch (Throwable e2) {
            throw new IOException(e2);
        }
    }

    public static void sendCoreDatabaseAsync()
    {
        Util.startDaemonThread("send-defect", new Runnable() {
            @Override
            public void run() {
                try {
                    doLogSendDefect(true, "core db", null, newHeader(), getCfgDatabase(),
                            Cfg.absRTRoot(), null, true, false, true);
                    l.warn("done");
                } catch (Throwable e) {
                    l.warn("can't send db. ignored: " + Util.e(e));
                }
            }
        });
    }

    public static void logSendDefectAsyncNoCfg(final boolean automatic,
            final String context, final Throwable e, final String user,
            final String rtRoot)
    {
        Util.startDaemonThread("send-defect", new Runnable() {
            @Override
            public void run()
            {
                logSendDefectSyncNoCfgIgnoreError(automatic, context, e, user, rtRoot);
            }
        });
    }

    public static void logSendDefectSyncNoCfgIgnoreError(boolean automatic,
            String context, Throwable e, String user, String rtRoot)
    {
        try {
            logSendDefectSyncNoCfg(automatic, context, e, user, rtRoot);
        } catch (Exception e2) {
            l.error("can't send defect sync: ", e2);
        }
    }

    public static void logSendDefectSyncIgnoreError(boolean automatic,
            String context, Throwable e)
    {
        try {
            logSendDefectSync(automatic, context, e);
        } catch (Exception e2) {
            l.error("can't send defect sync: ", e2);
        }
    }

    private static Map<Key, String> getCfgDatabase()
    {
        assert Cfg.inited();

        Map<Key, String> ret = Maps.newTreeMap();
        for (Key key : Key.values()) {
            // skip sensitive fields
            if (key == Key.CRED || key.keyString().startsWith("s3_") ||
                    key.keyString().startsWith("mysql_")) {
                continue;
            }

            String value = Cfg.db().getNullable(key);
            if (value != null && !value.equals(key.defaultValue())) ret.put(key, value);
        }

        return ret;
    }

    private static boolean isLastSentDefect(String message, String stack)
    {
        if (!Cfg.inited()) return false;

        try {
            DataInputStream is = new DataInputStream(
                    new FileInputStream(Util.join(Cfg.absRTRoot(), C.LAST_SENT_DEFECT)));
            try {
                return (message + stack).equals(is.readUTF());
            } finally {
                is.close();
            }
        } catch (FileNotFoundException e) {
            return false;
        } catch (Throwable ex) {
            l.warn("ignored: " + Util.e(ex));
            return false;
        }
    }

    private static void setLastSentDefect(String message, String stack)
    {
        if (!Cfg.inited()) return;

        try {
            DataOutputStream os = new DataOutputStream(
                    new FileOutputStream(Util.join(Cfg.absRTRoot(), C.LAST_SENT_DEFECT)));
            try {
                os.writeUTF(message + stack);
            } finally {
                os.close();
            }
        } catch (Throwable ex) {
            l.warn("ignored: " + Util.e(ex));
        }
    }

    public static void sendEmail(String from, String fromName, String to,
            @Nullable String replyTo, String subject, String textBody, @Nullable String htmlBody,
            boolean usingSendGrid, @Nullable String category)
            throws IOException
    {
        PBSVEmail.Builder bdEmail = PBSVEmail.newBuilder()
                                                .setFrom(from)
                                                .setFromName(fromName)
                                                .setTo(to)
                                                .setSubject(subject)
                                                .setTextBody(textBody)
                                                .setUsingSendgrid(usingSendGrid);

        if (replyTo != null)  bdEmail.setReplyTo(replyTo);
        if (htmlBody != null) bdEmail.setHtmlBody(htmlBody);
        if (category != null) bdEmail.setCategory(category);
        PBSVCall call = PBSVCall.newBuilder().setType(Type.EMAIL)
                                .setEmail(bdEmail).build();

        Socket s = send(call, 0);
        try {
            recv(s);
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            if (!s.isClosed()) s.close();
        }
    }



    /**
     * @param verbose false to collect as less data as possible
     * @param e may be null if no exception is available
     *
     * this method doesn't reference Cfg if it's not inited. exit the program
     * if the error is OutOfMemory
     */
    private static void doLogSendDefect(boolean automatic, String desc,
            @Nullable Throwable e, PBSVHeader header, Map<Key, String> cfgDB, String rtRoot,
            @Nullable String secret, boolean verbose, final boolean sendLogs, final boolean sendDB)
            throws Exception
    {
        if (e == null) e = new Exception(desc);
        String stackTrace = Util.stackTrace2string(e);

        if (Cfg.staging()) {
            l.warn("##### DEFECT #####\n" + desc + "\n" + Util.e(e));
            l.warn("(sv defect sending disabled on staging.)");
            return;
        }

        // always send non-automatic defects
        boolean isLastSent = automatic && isLastSentDefect(e.getMessage(), stackTrace);
        l.error((isLastSent ? "repeating last" : "sending") + " defect: " + desc + ": " + Util.e(e));
        if (isLastSent) return;

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

        PBSVDefect.Builder bdDefect = PBSVDefect.newBuilder().setAutomatic(automatic)
                .setDescription(sbDesc.toString())
                .setStacktrace(stackTrace);

        File fZippedFiles = File.createTempFile("$$$", "zip");

        if (verbose) {
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
            } catch (Exception e2) {
                // ignored
            }
            bdDefect.addJavaEnvName("uname -a");
            bdDefect.addJavaEnvValue(uname.get());

            // "file /bin/ls" result
            OutArg<String> fileRes = new OutArg<String>();
            fileRes.set("n/a");
            try {
                Util.execForeground(fileRes,"file", "/bin/ls");
            } catch (Exception e2) {
                //ignored
            }
            bdDefect.addJavaEnvName("file bin/ls");
            bdDefect.addJavaEnvValue(fileRes.get());

            // filesystem type
            if (Cfg.inited()) {
                bdDefect.addJavaEnvName("fs");
                try {
                    OutArg<Boolean> remote = new OutArg<Boolean>();
                    String fs = OSUtil.get().getFileSystemType(Cfg.db().getNullable(Key.ROOT),
                            remote);
                    bdDefect.addJavaEnvValue(fs + ", remote " + remote.get());
                } catch (Exception e2) {
                    bdDefect.addJavaEnvValue(e2.toString());
                }
            }

            // "df" result
            OutArg<String> df = new OutArg<String>();
            df.set("n/a");
            try {
                Util.execForeground(df, "df");
            } catch (Exception e2) {
                // ignored
            }
            bdDefect.addJavaEnvName("df");
            bdDefect.addJavaEnvValue(df.get());

            // java env
            for (Entry<Object, Object> en : System.getProperties().entrySet()) {
                bdDefect.addJavaEnvName(en.getKey().toString());
                bdDefect.addJavaEnvValue(en.getValue().toString());
            }

            // don't send defect log for SP or staging
            if (Cfg.inited() && Cfg.useArchive() && (sendLogs || sendDB)) {
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
                                            (sendDB && arg1.startsWith(C.CORE_DATABASE));
                                }
                            });

                    if (files == null) {
                        l.error("rtRoot not found");
                        files = new File[0];
                    }

                    l.info("compressing " + files.length + " logs/db files");

                    OutputStream os = new FileOutputStream(fZippedFiles);
                    try {
                        compressIgnoreErorr(files, os);
                    } finally {
                        os.close();
                    }
                } catch (Exception e2) {
                    l.error("failed compressing defect logs. send them as is: ", e);
                }
            }
        }

        PBSVCall call = PBSVCall.newBuilder().setType(Type.DEFECT)
                .setHeader(header)
                .setDefect(bdDefect).build();

        l.info("sending to sv");
        Socket s = send(call, fZippedFiles.length());
        try {
            OutputStream os = s.getOutputStream();
            InputStream isLogs = new FileInputStream(fZippedFiles);
            try {
                Util.copy(isLogs, os);
            } finally {
                isLogs.close();
            }

            recv(s);

        } finally {
            if (!s.isClosed()) s.close();
            FileUtil.deleteOrOnExit(fZippedFiles);
        }

        l.info("sending defect done");
        setLastSentDefect(e.getMessage(), stackTrace);

        if (Cfg.inited()) archiveLogs();

        if (e instanceof OutOfMemoryError) System.exit(C.EXIT_CODE_OUT_OF_MEMORY);
    }

    public static void sendAnalytics(File db) throws Exception
    {
        l.info("sending analytics");

        PBSVCall call = PBSVCall.newBuilder().setType(Type.ANALYTICS)
                .setHeader(newHeader())
                .setAnalytics(PBSVAnalytics.newBuilder()
                        .setDbDumpLen(db.length()))
                .build();

        Socket s = send(call, db.length());
        try {
            OutputStream os = s.getOutputStream();
            InputStream is = new FileInputStream(db);
            try {
                Util.copy(is, os);
            } finally {
                is.close();
            }
            recv(s);
        } finally {
            if (!s.isClosed()) s.close();
        }

        l.info("sending analytics done");
    }

    /**
     * the caller may append more data to <return value>.getOutputStream().
     * usage:
     *
     *  Socket s = send(...);
     *  try {
     *          ...;
     *          recv(s);
     *  } finally {
     *          if (!s.isClosed()) s.close();
     *  }
     *
     * @throws IOException
     */
    private static Socket send(PBSVCall call, long len) throws IOException
    {
        assert !Cfg.staging() || call.hasEmail();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        call.writeDelimitedTo(bos);

        //A couple of important points here:
        //TLS requires header to be HTTP/1.0, not HTTP/1.1
        //
        // We have to use sockets and not URLConnection because URLConnection has known bugs
        // - http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5026745 (but may not have been actually fixed)
        // URLConnection does not seem to flush the data
        //
        // Finally, when dealing with any HTTP server stuff, nginx must be configured to forward the right
        // amount of data using client_max_body_size 10M;

        String header = "POST " + "/sv_beta/sv" + " HTTP/1.0\r\n"
                        //+ "Host: " + SPSV.hostname() + ":" + SPSV.svPort() + "\r\n"
                        + "Connection: close\r\n"
                        + "Content-type: application/x-www-form-urlencoded\r\n"
                        + "Content-Length: " + (bos.size() + len) + "\r\n\r\n";

        SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault().createSocket(
                L.get().svHost(), L.get().svPort());

        OutputStream os = s.getOutputStream();
        try {
            os.write(header.getBytes());
            os.write(bos.toByteArray());
            return s;
        } catch (IOException e) {
            s.close();
            throw e;
        }
    }

    private static void recv(Socket s) throws Exception
    {
        DataInputStream dis = new DataInputStream(new BufferedInputStream(s.getInputStream()));

        try {
            StringBuilder sb = new StringBuilder();

            while (!(sb.toString().endsWith("\r\n\r\n"))) {
                sb.append((char) dis.readByte());
            }

            String str = sb.substring(sb.indexOf(CONTENT_LENGTH_HEADER)
                            + CONTENT_LENGTH_HEADER.length());
            str = str.substring(0, str.indexOf('\r'));
            int contentLength = Integer.parseInt(str);

            byte [] bs = new byte[contentLength];
            dis.readFully(bs);
            PBSVReply reply = PBSVReply.parseFrom(bs);
            if (reply.hasException()) throw Exceptions.fromPB(reply.getException());

        } finally {
            s.close();
        }
    }

    public static void rpc(PBSVCall call) throws Exception
    {
        Socket s = send(call, 0);
        try {
            recv(s);
        } finally {
            if (!s.isClosed()) s.close();
        }
    }

    // compress a single file into .gzip format
    public static void compressSlowly(File f, OutputStream os) throws IOException
    {
        OutputStream out = new GZIPOutputStream(os);
        try {
            InputStream in = new FileInputStream(f);
            try {
                byte[] bs = new byte[Param.FILE_BUF_SIZE];
                int read;
                while ((read = in.read(bs)) != -1) {
                    out.write(bs, 0, read);
                    Thread.yield();
                }
            } finally {
                in.close();
            }
        } finally {
            out.close();
        }
    }

    // archive + compress multiple files into a stream
    public static void compressIgnoreErorr(File[] files, OutputStream os)
        throws IOException
    {
        ZipOutputStream out = null;

        out = new ZipOutputStream(os);
        try {
            byte[] bs = new byte[Param.FILE_BUF_SIZE];
            for (File f : files) {
                try {
                    ZipEntry ze = new ZipEntry(f.getName());
                    out.putNextEntry(ze);

                    InputStream in = new FileInputStream(f);
                    try {
                        int read;
                        while ((read = in.read(bs)) != -1) {
                            out.write(bs, 0, read);
                        }
                    } finally {
                        in.close();
                    }
                } catch (IOException e) {
                    Util.l().warn("compress (ignored): " + Util.e(e));
                }
            }
        } finally {
            out.close();
        }
    }
}
