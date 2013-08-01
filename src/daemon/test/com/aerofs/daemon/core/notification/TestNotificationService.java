/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.notification.DownloadNotifier.DownloadThrottler;
import com.aerofs.daemon.core.notification.UploadNotifier.UploadThrottler;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus;
import com.aerofs.daemon.core.status.PathStatus;
import com.aerofs.daemon.core.syncstatus.AggregateSyncStatus;
import com.aerofs.daemon.core.syncstatus.SyncStatusSynchronizer;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.ChannelFactories;
import com.aerofs.ritual_notification.MockRNSConfiguration;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.aerofs.ritual_notification.RitualNotifier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import static org.mockito.Mockito.*;

public class TestNotificationService
{
    @Spy RitualNotifier _ritualNotifier;
    RitualNotificationServer _rns;

    @Mock CoreScheduler _coreScheduler;
    @Mock DirectoryService _directoryService;
    @Mock UserAndDeviceNames _userAndDeviceNames;
    @Mock DownloadState _downloadState;
    @Mock UploadState _uploadState;
    @Mock BadCredentialNotifier _badCredentialNotifier;
    @Mock PathStatus _pathStatus;
    @Mock SyncStatusSynchronizer _syncStatusSynchronizer;
    @Mock AggregateSyncStatus _aggregateSyncStatus;
    @Mock ServerConnectionStatus _serverConnectionStatus;
    @Mock ConflictNotifier _conflictNotifier;
    @Mock DownloadThrottler _downloadThrottler;
    @Mock UploadThrottler _uploadThrottler;
    NotificationService _service;

    @Rule public TemporaryFolder _approotFolder;

    @Before
    public void setup() throws Exception
    {
        MockitoAnnotations.initMocks(this);

        _approotFolder = new TemporaryFolder();
        _approotFolder.create();
        AppRoot.set(_approotFolder.getRoot().getAbsolutePath());

        _rns = new RitualNotificationServer(ChannelFactories.getServerChannelFactory(),
                _ritualNotifier, new MockRNSConfiguration());

        _service = new NotificationService(_coreScheduler, _rns, _directoryService,
                _userAndDeviceNames, _downloadState, _uploadState, _badCredentialNotifier,
                _pathStatus, _syncStatusSynchronizer, _aggregateSyncStatus, _serverConnectionStatus,
                _conflictNotifier, _downloadThrottler, _uploadThrottler);
    }

    @Test
    public void shouldSetupRitualNotifierListenerOnInit()
    {
        _service.init_();
        verify(_rns.getRitualNotifier()).addListener(_service);
    }
}
