package com.aerofs.sv.client;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.RecentExceptions;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Sv.PBSVCall;
import com.aerofs.proto.Sv.PBSVCall.Type;
import com.aerofs.proto.Sv.PBSVDefect;
import com.aerofs.proto.Sv.PBSVDefect.Builder;
import com.aerofs.proto.Sv.PBSVGzippedLog;
import com.aerofs.proto.Sv.PBSVHeader;
import com.aerofs.rocklog.Defect;
import com.aerofs.rocklog.Defect.Priority;
import com.aerofs.rocklog.RockLog;
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
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.MissingResourceException;
import java.util.Stack;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.aerofs.lib.FileUtil.deleteOrOnExit;
import static com.aerofs.lib.LibParam.FILE_BUF_SIZE;
import static com.aerofs.lib.ThreadUtil.startDaemonThread;
import static com.aerofs.lib.Util.crc32;
import static com.aerofs.lib.Util.deleteOldHeapDumps;
import static com.aerofs.lib.Util.e;
import static com.aerofs.lib.Util.join;
import static com.aerofs.lib.cfg.Cfg.absRTRoot;
import static com.aerofs.lib.os.OSUtil.OSArch.X86;
import static com.aerofs.lib.os.OSUtil.OSArch.X86_64;

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
    private static final String SV_URL =
            getStringProperty("lib.sv.url", "https://sv.aerofs.com:443/sv_beta/sv");

    private static final SVRPCClient client = new SVRPCClient(SV_URL);

    private static SVRPCClient getRpcClient()
    {
        return client;
    }

    private static final RecentExceptions recentExceptions = RecentExceptions.getInstance();

    // Create a new instance of RockLog for SVClient
    // Ideally we would use a shared instance provided in the SVClient constructor, but since all
    // SVClient methods are static, that won't work... Or we could have something like
    // SVClient.init(RockLog), but I'm not fan of init methods either.
    private static final RockLog rockLog = new RockLog();


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
     * @param defectContents the data the defect contains
     * <strong>{@code defectContents} should never be printed into the log file!</strong>
     */
    public static void logSendDefectSync(boolean isAutoBug, String desc, @Nullable Throwable cause, @Nullable String defectContents, boolean sendFileNames)
            throws IOException, AbstractExWirable
    {
        doLogSendDefect(
                isAutoBug,
                desc,
                cause,
                newHeader(),
                absRTRoot(),
                defectContents,
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
            String description,
            @Nullable Throwable cause,
            PBSVHeader header,
            String rtRoot,
            @Nullable String defectContents, // DO NOT PRINT THIS IN PRODUCTION!
            final boolean sendLogs,
            final boolean sendDB,
            final boolean sendHeapDumps,
            final boolean sendUnobfuscatedFileMapping)
            throws IOException, AbstractExWirable
    {
        l.debug("build defect");

        if (cause == null) cause = new Exception(description); // FIXME (AG): bogus
        String stackTrace = Exceptions.getStackTraceAsString(cause);

        // If we have any LinkageError (NoClassDefFoundError or UnsatisfiedLinkError) or
        // MissingResourceException, this probably indicates that our stripped-down version of
        // OpenJDK is missing something. Send a different RockLog defect to make sure we catch it.
        if (cause instanceof LinkageError || cause instanceof MissingResourceException) {
            rockLog.newDefect("system.classnotfound").setException(cause).send();
        } else {
            // Note: we can't pick a good defect name here since we don't know who created this
            // defect, so we use the generic "svdefect" string. See doc in newDefect() for more
            // info about defect names.

            Defect.Priority priority = isAutoBug ? Priority.Auto : Priority.User;
            String defectName = cause.getStackTrace().length == 0 ? "svdefect" : cause.getMessage();
            rockLog.newDefect(defectName).setMessage(description).setPriority(priority).setException(cause).send();
        }

        // FIXME (AG): dump healthcheck diagnostics here

        // always send non-automatic defects and database requests
        boolean ignoreDefect = isAutoBug && recentExceptions.isRecent(cause) && !sendDB;
        l.error((ignoreDefect ? "repeating last" : "sending") + " defect: " + description + ": " + Util.e(cause));
        if (ignoreDefect) return;

        StringBuilder sbDesc = createDefectDescription(description, defectContents);

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

    private static StringBuilder createDefectDescription(String baseDescription, String defectContents)
    {
        StringBuilder sbDesc = new StringBuilder();

        if (baseDescription != null) {
            sbDesc.append(baseDescription);
            sbDesc.append(": ");
        }

        if (defectContents != null) {
            sbDesc.append("\n");
            sbDesc.append(defectContents);
            sbDesc.append("\n");
        }
        return sbDesc;
    }

    private static File compressDefectLogs(String rtRoot, final boolean sendLogs,
            final boolean sendDB, final boolean sendHeapDumps, boolean sendUnobfuscatedFileMapping)
    {
        File defectFilesZip = null;
        if (Cfg.inited() && (sendLogs || sendDB || sendHeapDumps || sendUnobfuscatedFileMapping)) {
            try {
                // add log files
                File[] files = new File(rtRoot).listFiles(
                        new FilenameFilter() {
                            @Override
                            public boolean accept(File arg0, String arg1)
                            {
                                // Note: the core database consists of three files:
                                // db, db-wal, and db-shm.
                                return (sendLogs && arg1.endsWith(LibParam.LOG_FILE_EXT))
                                        ||
                                        (sendDB && (arg1.startsWith(LibParam.OBF_CORE_DATABASE) ||
                                                            arg1.endsWith("wal")        ||
                                                            arg1.endsWith("shm")))
                                        ||
                                        (sendHeapDumps && arg1.endsWith(LibParam.HPROF_FILE_EXT));
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
        String contactEmail = cfgDB.get(Key.CONTACT_EMAIL);

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

            // architecture
            bdDefect.addJavaEnvName("arch");
            switch (OSUtil.getOSArch()) {
                case X86: bdDefect.addJavaEnvValue("x86"); break;
                case X86_64: bdDefect.addJavaEnvValue("amd64"); break;
                default: bdDefect.addJavaEnvValue("unknown"); break;
            }

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
            bdDefect.addJavaEnvName("rtroot");
            bdDefect.addJavaEnvValue(rtRoot + ", "
                    + getFSType(rtRoot) + ", "
                    + getDiskusage(rtRoot));

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

        if (Cfg.inited() && Cfg.storageType() == StorageType.LINKED) {
            try {
                for (Entry<SID, String> root : Cfg.getRoots().entrySet()) {
                    String absPath = root.getValue();
                    bdDefect.addJavaEnvName("root " + root.getKey().toStringFormal());
                    bdDefect.addJavaEnvValue(absPath + ", "
                            + getFSType(absPath) + ", "
                            + getDiskusage(absPath));
                }
            } catch (SQLException e) {
                l.error("ignored exception", e);
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


    private static String getFSType(String absPath)
    {
        try {
            OutArg<Boolean> remote = new OutArg<Boolean>();
            String fs = OSUtil.get().getFileSystemType(absPath, remote);
            return (remote.get() ? "remote " : "") + fs;
        } catch (Throwable t) {
            return t.toString();
        }
    }

    private static String getDiskusage(String absPath)
    {
        try {
            return listFreeSpaceOnPartition(absPath);
        } catch (Throwable t) {
            return t.toString();
        }
    }

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

        return "free:" + dir.getFreeSpace() + " usable:" + dir.getUsableSpace() + " total:" + dir.getTotalSpace();
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
        stack.push(Cfg.absDefaultRootAnchor());

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
