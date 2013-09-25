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
import com.aerofs.daemon.core.online_status.OnlineStatusNotifier;
import com.aerofs.daemon.core.status.PathStatus;
import com.aerofs.daemon.core.syncstatus.AggregateSyncStatus;
import com.aerofs.daemon.core.syncstatus.SyncStatusSynchronizer;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.lib.AppRoot;
import com.aerofs.ritual_notification.MockRNSConfiguration;
import com.aerofs.ritual_notification.RitualNotificationServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static com.aerofs.lib.ChannelFactories.getServerChannelFactory;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class TestNotificationService
{
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
    @Mock OnlineStatusNotifier _onlineStatusNotifier;

    @Rule public TemporaryFolder _approotFolder;

    MockRNSConfiguration _config;
    RitualNotificationServer _rns;

    NotificationService _service; // SUT

    @Before
    public void setup() throws Exception
    {
        MockitoAnnotations.initMocks(this);

        _approotFolder = new TemporaryFolder();
        _approotFolder.create();

        AppRoot.set(_approotFolder.getRoot().getAbsolutePath());

        _config = new MockRNSConfiguration();
        _rns = spy(new RitualNotificationServer(getServerChannelFactory(), _config));

        _service = new NotificationService(
                _coreScheduler,
                _rns,
                _directoryService,
                _userAndDeviceNames,
                _downloadState,
                _uploadState,
                _badCredentialNotifier,
                _pathStatus,
                _syncStatusSynchronizer,
                _aggregateSyncStatus,
                _serverConnectionStatus,
                _conflictNotifier,
                _downloadThrottler,
                _uploadThrottler,
                _onlineStatusNotifier);
    }

    @Test
    public void shouldSetupNotificationServiceOnInit()
            throws IOException
    {
        // FIXME the current test case only cover two of many services that should be setup.
        //   The reason for that is covering other services requires non-trivial refactoring
        //   of other services to make them testable, and is out-of-scope at the time when
        //   this test case is written.
        _service.init_();
        verify(_rns).addListener(_service);
        verify(_onlineStatusNotifier).init_();
    }
}
