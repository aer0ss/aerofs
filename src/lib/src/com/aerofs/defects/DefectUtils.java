/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseUtil;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.BaseCfg;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgKey;
import com.aerofs.lib.os.OSUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

import static com.aerofs.lib.FileUtil.deleteOrOnExit;
import static com.aerofs.lib.Util.*;
import static com.aerofs.lib.cfg.Cfg.absRTRoot;
import static com.google.common.base.Preconditions.checkState;
import static org.apache.commons.lang.ArrayUtils.add;
import static org.apache.commons.lang.ArrayUtils.isEmpty;

class DefectUtils
{
    private static final Logger l = LoggerFactory.getLogger(DefectUtils.class);

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    private DefectUtils()
    {
        // prevent instantiation
    }

    static String newDefectID()
    {
        return UniqueID.generate().toStringFormal();
    }

    static String getTimeStamp()
    {
        SimpleDateFormat date = new SimpleDateFormat(DATE_FORMAT);
        date.setTimeZone(TimeZone.getTimeZone("UTC"));
        return date.format(new Date());
    }

    /**
     * Recursively converts a Throwable and its causes into JSON using the following format:
     *
     * {
     *   type: "java.io.IOException",
     *   message: "Something happened",
     *   stacktrace: [
     *     { class: "com.aerofs.Lol", method: "doWork", file: "Lol.java", line:32 },
     *     { class: "com.aerofs.Foo", method: "foo", file: "Foo.java", line:10 },
     *     ...
     *   ],
     *   cause: {
     *      type: "java.lang.NullPointerException"
     *      message: "Oops!"
     *      stacktrace: [...]
     *      cause: {}
     *   }
     * }
     */
    static HashMap<String, Object> encodeException(Throwable e)
    {
        if (e == null) return null;

        List<Object> frames = Lists.newArrayList();
        for (StackTraceElement f : e.getStackTrace()) {
            HashMap<String, Object> frame = Maps.newHashMap();
            frame.put("class", f.getClassName());
            frame.put("method", f.getMethodName());
            frame.put("file", f.getFileName());
            frame.put("line", f.getLineNumber());
            frames.add(frame);
        }

        HashMap<String, Object> result = Maps.newHashMap();
        result.put("type", e.getClass().getName());
        result.put("message", e.getMessage());
        result.put("stacktrace", frames);
        result.put("cause", encodeException(e.getCause()));

        return result;
    }

    static String getCommandOutput(String... cmd)
    {
        OutArg<String> result = new OutArg<String>();
        result.set("n/a");
        try {
            SystemUtil.execForeground(result, cmd);
        } catch (Throwable t) {
            // ignored
            l.warn("Failed command {}: {}", cmd, t.toString());
        }
        return result.get();
    }

    static String getDirectoryInfo(String absPath)
    {
        return absPath + ", " + getFSType(absPath) + ", " + getDiskusage(absPath);
    }

    private static String getFSType(String absPath)
    {
        try {
            OutArg<Boolean> remote = new OutArg<Boolean>();
            String fs = OSUtil.get().getFileSystemType(absPath, remote);
            return (remote.get() ? "remote " : "") + fs;
        } catch (Throwable t) {
            l.warn("Failed to get file system type of {}: {}", absPath, t.toString());
            return t.toString();
        }
    }

    private static String getDiskusage(String absPath)
    {
        try {
            File dir = new File(absPath);
            checkState(dir.exists(), absPath + " doesn't exist");

            return "free:" + dir.getFreeSpace() +
                    " usable:" + dir.getUsableSpace() +
                    " total:" + dir.getTotalSpace();
        } catch (Throwable t) {
            l.warn("Failed to get disk usage of {}: {}", absPath, t.toString());
            return t.toString();
        }
    }

    /**
     * List files (along with their sizes) in the path
     * FIXME (AG): what happens if the user has put AeroFS in a bad place (i.e. under cache, etc)
     */
    static String listTopLevelContents(String absPath)
    {
        try {
            File dir = new File(absPath);
            checkState(dir.isDirectory(), absPath + " is not a directory");

            File[] children = dir.listFiles();
            if (isEmpty(children)) {
                return "empty";
            }

            StringBuilder sb = new StringBuilder();

            for (File f : children) {
                // skip the AeroFS folder (could be arbitrarily deep), but log it
                if (f.isDirectory() && f.getName().equalsIgnoreCase("AeroFS")) {
                    sb.append(f.getName()).append(": ").append("IGNORED");
                } else {
                    sb.append(f.getName()).append(": ").append(BaseUtil.getDirSize(f));
                }

                sb.append('\n');
            }

            return sb.toString();
        } catch (Throwable t) {
            l.warn("Failed to list top level contents of {}: {}", absPath, t.toString());
            return t.toString();
        }
    }

    static String listLinkedRoots()
    {
        try {
            Map<SID, String> roots = new CfgAbsRoots().getAll();

            StringBuilder sb = new StringBuilder();
            for (Entry<SID, String> root : roots.entrySet()) {
                sb.append("root ")
                        .append(root.getKey().toStringFormal())
                        .append(": ")
                        .append(getDirectoryInfo(root.getValue()))
                        .append("\n");
            }
            return sb.toString();
        } catch (Throwable t) {
            l.warn("Failed to list linked roots: {}", t.toString());
            return t.toString();
        }
    }

    static String listCfgDBContent()
    {
        try {
            Map<CfgKey, String> cfgDB = BaseCfg.getInstance().dumpDB();

            StringBuilder sb = new StringBuilder();
            for (Entry<CfgKey, String> entry : cfgDB.entrySet()) {
                sb.append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append("\n");
            }
            return sb.toString();
        } catch (Throwable t) {
            l.warn("Failed to dump cfg db: {}", t.toString());
            return t.toString();
        }
    }

    static File[] getFiles(String absRTRoot, final boolean uploadLogs, final boolean uploadDB,
            final boolean uploadHeapDumps, boolean uploadFilenames, final boolean uploadAllFiles)
    {
        File[] files = new File(absRTRoot).listFiles((file, filename) -> uploadAllFiles
                || (uploadLogs && filename.endsWith(LibParam.LOG_FILE_EXT))
                || (uploadDB && (filename.startsWith(LibParam.OBF_CORE_DATABASE)
                                       || filename.endsWith("wal")
                                       || filename.endsWith("shm")))
                || (uploadHeapDumps && filename.endsWith(LibParam.HPROF_FILE_EXT)));

        if (files == null) {
            l.error("rtroot not found");
            files = new File[0];
        }

        if (uploadFilenames) {
            files = (File[])add(files, createNameMapFile());
        }

        return files;
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
            l.warn("create temp file failed: ", e);
            return null;
        }
    }

    /**
     * DFS on the AeroFS folder and write every file name with its crc32 version.
     */
    private static void writeFileNames(BufferedWriter bw) throws IOException
    {
        Stack<String> stack = new Stack<>();
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

    static void deleteOldHeapDumps()
    {
        File[] heapDumps = new File(absRTRoot()).listFiles((file, filename) -> {
            return filename.endsWith(LibParam.HPROF_FILE_EXT);
        });
        if (heapDumps == null) {
            l.error("rtRoot not found.");
            return;
        }
        for (File heapDumpFile : heapDumps) {
            l.debug("Deleting old heap dump: " + heapDumpFile);
            deleteOrOnExit(heapDumpFile);
            heapDumpFile.delete();
        }
    }
}
