/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui.logs;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExInternalError;
import com.aerofs.defects.DryadClient;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import static com.aerofs.defects.DryadClientUtil.createArchivedLogsResource;
import static com.aerofs.defects.DryadClientUtil.createPublicDryadClient;
import static com.aerofs.lib.FileUtil.deleteOrOnExit;
import static com.aerofs.lib.LibParam.FILE_BUF_SIZE;
import static com.aerofs.lib.ThreadUtil.sleepUninterruptable;
import static com.aerofs.lib.ThreadUtil.startDaemonThread;
import static com.aerofs.ui.UIParam.DM_LOG_ARCHIVE_INTERVAL;
import static com.aerofs.ui.UIParam.DM_LOG_ARCHIVE_STARTUP_DELAY;

public final class LogArchiver
{
    private static final Logger l = Loggers.getLogger(LogArchiver.class);

    public static final FilenameFilter ROLLED_UNGZIPPED_LOG_FILTER = new FilenameFilter()
    {
        @Override
        public boolean accept(File arg0, String arg1)
        {
            return arg1.contains(LibParam.LOG_FILE_EXT + ".") && !arg1.endsWith(".gz");
        }
    };

    public static final FilenameFilter GZIPPED_LOG_FILTER = new FilenameFilter()
    {
        @Override
        public boolean accept(File arg0, String arg1)
        {
            return arg1.contains(LibParam.LOG_FILE_EXT + ".") && arg1.endsWith(".gz");
        }
    };

    private final String _logpath;

    /*
     * @param logpath path in which we should look for logs to archive
     */
    public LogArchiver(String logpath)
    {
        this._logpath = logpath;
    }

    public void start()
    {
        startDaemonThread("archiver", new Runnable()
        {
            @Override
            public void run()
            {
                l.info("start archiver");

                sleepUninterruptable(DM_LOG_ARCHIVE_STARTUP_DELAY);

                while (true) {
                    archiveLogs();
                    sleepUninterruptable(DM_LOG_ARCHIVE_INTERVAL);
                }
            }
        });
    }

    /**
     * Archive the logs and delete them once archiving is done
     *
     * IMPORTANT NOTES:
     * <ol>
     *     <li>This is package-private so I can test out logarchiver</li>
     *     <li>It's synchronized because both the running thread and a
     *     <em>potential</em> caller share a resource: the filesystem</li>
     * </ol>
     */
    synchronized void archiveLogs()
    {
        boolean havelogs = compressLogs(_logpath);
        if (havelogs) {
            uploadCompressedLogs(_logpath);
        }
    }

    private static boolean compressLogs(String logpath)
    {
        File[] uncompressedLogs = new File(logpath).listFiles(ROLLED_UNGZIPPED_LOG_FILTER);
        if (uncompressedLogs == null) {
            l.debug("no logs to compress");
            return false;
        }

        for (File uncompressedLog : uncompressedLogs) {
            l.debug("archive " + uncompressedLog);

            try {
                FileOutputStream os = new FileOutputStream(uncompressedLog.getPath() + ".gz");
                try {
                    doYieldingCompress(uncompressedLog, os);
                } finally {
                    os.close();
                }

                uncompressedLog.delete();
            } catch (IOException e) {
                l.warn("archive" + uncompressedLog + ": " + Util.e(e));
            }
        }

        return true;
    }

    private static void uploadCompressedLogs(String logpath)
    {
        File[] gzippedLogs = new File(logpath).listFiles(GZIPPED_LOG_FILTER);
        for (File gzippedLog : gzippedLogs) {
            l.debug("upload {}", gzippedLog);

            try {
                DryadClient dryad = createPublicDryadClient();
                dryad.uploadFiles(
                        createArchivedLogsResource(Cfg.user(), Cfg.did(), gzippedLog.getName()),
                        new File[]{gzippedLog});
                deleteOrOnExit(gzippedLog);
            } catch (Exception e) {
                // suppress stack for ExInternalError. It has caused too much logging due to SV
                // problems.
                l.warn("upload {}: {}", gzippedLog, Util.e(e, ExInternalError.class));
            }
        }
    }

    /**
     * gzip a single file; process a chunk at a time, and yield the thread
     * after the chunk is processed
     *
     * @param f file to compress
     * @param os {@link OutputStream} to which the gzipped data is written
     * @throws IOException on any error
     */
    private static void doYieldingCompress(File f, OutputStream os) throws IOException
    {
        OutputStream out = new GZIPOutputStream(os);
        try {
            InputStream in = new FileInputStream(f);
            try {
                byte[] bs = new byte[FILE_BUF_SIZE];
                int read;
                while ((read = in.read(bs)) != -1) {
                    out.write(bs, 0, read);
                    out.flush();

                    Thread.yield();
                }
            } finally {
                in.close();
            }
        } finally {
            out.close();
        }
    }
}
