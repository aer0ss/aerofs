/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.C;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferProgress;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferredItem;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.tcp.TCP;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.aerofs.ritual_notification.RitualNotifier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public abstract class AbstractTestNotifier
{
    @Mock TCP _tcp;

    @Mock DirectoryService _directoryService;
    @Mock UserAndDeviceNames _userAndDeviceNames;
    @Mock RitualNotificationServer _notificationServer;
    @Mock RitualNotifier _ritualNotifier;

    OID _oid;
    DID _did;

    protected abstract void setUpImpl();
    protected abstract void teardownImpl();
    protected abstract void disableMetaFilter();
    protected abstract void performAction(TransferredItem key, TransferProgress value);
    protected abstract void verifyUntracked();

    @Before public void setUp()
    {
        PowerMockito.mockStatic(System.class);

        when(_notificationServer.getRitualNotifier()).thenReturn(_ritualNotifier);

        _oid = OID.generate();
        _did = DID.generate();

        setUpImpl();
    }

    @After public void teardown()
    {
        teardownImpl();
    }

    @Test public void shouldFilterMeta()
    {
        invoke(true, 50, 100);
        verify(_ritualNotifier, never()).sendNotification(any(PBNotification.class));
    }


    @Test public void shouldNotFilterMeta()
    {
        disableMetaFilter();
        invoke(true, 50, 100);
        verify(_ritualNotifier, times(1)).sendNotification(any(PBNotification.class));
    }

    @Test public void shouldThrottle()
    {
        configureTimes(10L, 100L, 200L, 300L);
        invoke(false, 10, 100);
        invoke(false, 20, 100);
        invoke(false, 30, 100);
        invoke(false, 40, 100);
        verify(_ritualNotifier, times(1)).sendNotification(any(PBNotification.class));
    }

    @Test public void shouldNotThrottle()
    {
        configureTimes(1 * C.SEC,
                10 * C.SEC, 10 * C.SEC + 1, // elapsed(), construct
                20 * C.SEC, 20 * C.SEC + 1, // elapsed(), construct
                30 * C.SEC, 30 * C.SEC + 1); // elapsed(), construct
        invoke(false, 10, 100);
        invoke(false, 20, 100);
        invoke(false, 30, 100);
        invoke(false, 40, 100);
        verify(_ritualNotifier, times(4)).sendNotification(any(PBNotification.class));
    }

    @Test public void shouldNotThrottleCompleted()
    {
        configureTimes(10L * C.NSEC_PER_MSEC, 100L * C.NSEC_PER_MSEC);
        invoke(false, 10, 100);
        invoke(false, 100, 100);
        verify(_ritualNotifier, times(2)).sendNotification(any(PBNotification.class));
    }

    @Test public void shouldUntrack()
    {
        configureTimes(10L * C.NSEC_PER_MSEC, 100L * C.NSEC_PER_MSEC);
        invoke(false, 10, 100);
        invoke(false, 100, 100);
        verifyUntracked();
    }

    private void configureTimes(Long time0, Long... times)
    {
        Long[] nanotimes = new Long[times.length];
        for (int i = 0; i < times.length; i++) {
            nanotimes[i]   = times[i] * C.NSEC_PER_MSEC; // for elapsed()
        }
        when(System.nanoTime()).thenReturn(time0 * C.NSEC_PER_MSEC, nanotimes);
    }

    void invoke(boolean isMeta, long done, long total)
    {
        SOCID socid = new SOCID(new SIndex(0), _oid, isMeta ? CID.META : CID.CONTENT);
        Endpoint ep = new Endpoint(_tcp, _did);
        TransferredItem key = new TransferredItem(socid, ep);
        TransferProgress value = new TransferProgress(done, total);

        performAction(key, value);
    }
}
