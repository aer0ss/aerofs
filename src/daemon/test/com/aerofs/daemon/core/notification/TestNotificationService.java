/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.online_status.OnlineStatusNotifier;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.nativesocket.NativeSocketAuthenticatorFactory;
import com.aerofs.lib.nativesocket.RitualNotificationSocketFile;
import com.aerofs.ritual_notification.RitualNotificationServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Properties;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestNotificationService
{
    @Mock CoreScheduler sched;
    @Mock DirectoryService ds;
    @Mock DownloadState dls;
    @Mock UploadState uls;
    @Mock BadCredentialNotifier badCredentialNotifier;
    @Mock PathStatusNotifier pathStatusNotifier;
    @Mock ConflictNotifier conflictNotifier;
    @Mock DownloadNotifier downloadNotifier;
    @Mock UploadNotifier uploadNotifier;
    @Mock OnlineStatusNotifier onlineStatusNotifier;

    @Rule public TemporaryFolder _approotFolder;

    @Mock RitualNotificationSocketFile _ritualNotificationSocketFile;
    RitualNotificationServer rns;

    NotificationService _service; // SUT

    @Before
    public void setup() throws Exception
    {
        MockitoAnnotations.initMocks(this);

        _approotFolder = new TemporaryFolder();
        _approotFolder.create();

        TemporaryFolder rnsSockFolder = new TemporaryFolder();
        rnsSockFolder.create();
        File tempRnsSocketFile = rnsSockFolder.newFile("temp_rns_TNS.sock");

        AppRoot.set(_approotFolder.getRoot().getAbsolutePath());
        ConfigurationProperties.setProperties(new Properties());

        when(_ritualNotificationSocketFile.get()).thenReturn(tempRnsSocketFile);

        rns = spy(new RitualNotificationServer(_ritualNotificationSocketFile,
                NativeSocketAuthenticatorFactory.create()));

        _service = new NotificationService(sched, rns, dls, downloadNotifier, uls, uploadNotifier,
                badCredentialNotifier, conflictNotifier, pathStatusNotifier,
                onlineStatusNotifier,
                Collections.<ISnapshotableNotificationEmitter>emptySet());
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
        verify(rns).addListener(_service);
        verify(onlineStatusNotifier).init_();
    }
}
