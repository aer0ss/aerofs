/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.C;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.notification.UploadNotifier.UploadThrottler;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferProgress;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferredItem;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.tcp.TCP;
import com.aerofs.lib.Throttler;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.ritual_notification.RitualNotifier;
import com.aerofs.ritual_notification.RitualNotificationServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Throttler.class)
@PowerMockIgnore({"ch.qos.logback.*", "org.slf4j.*"})
public class TestUploadNotifier
{
    @Mock TCP _tcp;

    @Mock DirectoryService _directoryService;
    @Mock UserAndDeviceNames _userAndDeviceNames;
    @Mock RitualNotificationServer _notificationServer;
    @Mock RitualNotifier _ritualNotifier;
    @Spy UploadThrottler _throttler = new UploadThrottler();

    UploadNotifier _uploadNotifier;

    OID _oid;
    DID _did;

    @Before public void setUp()
    {
        PowerMockito.mockStatic(System.class);

        _throttler.setDelay(1 * C.SEC);

        when(_notificationServer.getRitualNotifier()).thenReturn(_ritualNotifier);

        _uploadNotifier = new UploadNotifier(_directoryService, _userAndDeviceNames, _notificationServer, _throttler);
        _uploadNotifier.filterMeta_(true);

        _oid = OID.generate();
        _did = DID.generate();
    }

    @After public void teardown()
    {
        _throttler.clear();
    }

    @Test public void shouldFilterMeta()
    {
        invoke(true, 50, 100);
        verify(_ritualNotifier, never()).sendNotification(any(PBNotification.class));
    }

    @Test public void shouldNotFilterMeta()
    {
        _uploadNotifier.filterMeta_(false);
        invoke(true, 50, 100);
        verify(_ritualNotifier, times(1)).sendNotification(any(PBNotification.class));
    }

    @Test public void shouldThrottle()
    {
        when(System.currentTimeMillis()).thenReturn(10L, 100L, 200L, 300L);
        invoke(false, 10, 100);
        invoke(false, 20, 100);
        invoke(false, 30, 100);
        invoke(false, 40, 100);
        verify(_ritualNotifier, times(1)).sendNotification(any(PBNotification.class));
    }

    @Test public void shouldNotThrottle()
    {
        when(System.currentTimeMillis()).thenReturn(1 * C.SEC, 10 * C.SEC, 20 * C.SEC, 30 * C.SEC);
        invoke(false, 10, 100);
        invoke(false, 20, 100);
        invoke(false, 30, 100);
        invoke(false, 40, 100);
        verify(_ritualNotifier, times(4)).sendNotification(any(PBNotification.class));
    }

    @Test public void shouldNotThrottleCompleted()
    {
        when(System.currentTimeMillis()).thenReturn(10L, 100L);
        invoke(false, 10, 100);
        invoke(false, 100, 100);
        verify(_ritualNotifier, times(2)).sendNotification(any(PBNotification.class));
    }

    @Test public void shouldUntrack()
    {
        when(System.currentTimeMillis()).thenReturn(10L, 100L);
        invoke(false, 10, 100);
        invoke(false, 100, 100);
        verify(_throttler, times(1)).untrack(any(TransferredItem.class));
    }

    void invoke(boolean isMeta, long done, long total)
    {
        SOCID socid = new SOCID(new SIndex(0), _oid, isMeta ? CID.META : CID.CONTENT);
        Endpoint ep = new Endpoint(_tcp, _did);
        TransferredItem key = new TransferredItem(socid, ep);
        TransferProgress value = new TransferProgress(done, total);

        _uploadNotifier.onTransferStateChanged_(key, value);
    }
}
