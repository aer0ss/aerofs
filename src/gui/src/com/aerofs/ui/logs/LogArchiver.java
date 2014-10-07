/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui.logs;

import com.aerofs.base.Loggers;
import com.aerofs.defects.DryadClient;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.cfg.Cfg;
import org.slf4j.Logger;

import java.io.File;
import java.io.FilenameFilter;

import static com.aerofs.defects.DryadClientUtil.createArchivedLogsResource;
import static com.aerofs.defects.DryadClientUtil.createPublicDryadClient;
import static com.aerofs.lib.FileUtil.deleteOrOnExit;
import static com.aerofs.lib.ThreadUtil.sleepUninterruptable;
import static com.aerofs.lib.ThreadUtil.startDaemonThread;
import static com.aerofs.ui.UIParam.DM_LOG_ARCHIVE_INTERVAL;
import static com.aerofs.ui.UIParam.DM_LOG_ARCHIVE_STARTUP_DELAY;
import static com.google.common.base.Objects.firstNonNull;

public final class LogArchiver
{
    private static final Logger l = Loggers.getLogger(LogArchiver.class);

    public static final FilenameFilter ROLLED_UNGZIPPED_LOG_FILTER = (file, filename) ->
            filename.contains(LibParam.LOG_FILE_EXT + ".") && !filename.endsWith(".gz");

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
        startDaemonThread("archiver", () -> {
            l.info("start archiver");

            sleepUninterruptable(DM_LOG_ARCHIVE_STARTUP_DELAY);

            while (true) {
                uploadArchivedLogs();
                sleepUninterruptable(DM_LOG_ARCHIVE_INTERVAL);
            }
        });
    }

    /**
     * Upload the logs and compress on-the-fly
     */
    synchronized void uploadArchivedLogs()
    {
        File[] logs = new File(_logpath).listFiles(ROLLED_UNGZIPPED_LOG_FILTER);
        for (File log : firstNonNull(logs, new File[0])) {
            try {
                DryadClient dryad = createPublicDryadClient();
                String resource = createArchivedLogsResource(Cfg.user(), Cfg.did(),
                        log.getName() + ".gz");
                dryad.uploadFiles(resource, log);
                deleteOrOnExit(log);
            } catch (Exception e) {
                l.warn("Failed to upload archived logs {}: {}", log, e.toString());
            }
        }
    }
}
