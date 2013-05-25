/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.first_launch;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.notification.RitualNotificationServer;
import com.aerofs.proto.RitualNotifications.PBIndexingProgress;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.google.inject.Inject;
import org.slf4j.Logger;

/**
 * Allows the Scanner to report progress during the first scan (i.e. initial indexing)
 */
public class ScanProgressReporter
{
    private static final Logger l = Loggers.getLogger(ScanProgressReporter.class);

    private int _files;
    private int _folders;
    private boolean _done;

    private final RitualNotificationServer _rns;

    @Inject
    public ScanProgressReporter(RitualNotificationServer rns)
    {
        _rns = rns;
    }

    void onFirstLaunchCompletion_()
    {
        _done = true;
    }

    public void folderScanned_(int files)
    {
        if (_done) return;

        ++_folders;
        filesScanned_(files);
    }

    public void filesScanned_(int files)
    {
        if (_done || files == 0) return;

        _files += files;

        l.debug("progress: {} -> {} / {}", files, _files, _folders);

        // TODO: batch notifications?
        _rns.sendEvent_(PBNotification.newBuilder()
                .setType(Type.INDEXING_PROGRESS)
                .setIndexingProgress(PBIndexingProgress.newBuilder()
                        .setFiles(_files)
                        .setFolders(_folders))
                .build());
    }
}
