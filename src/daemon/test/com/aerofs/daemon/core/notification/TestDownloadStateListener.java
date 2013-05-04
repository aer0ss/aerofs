/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.protocol.IDownloadStateListener.Ended;
import com.aerofs.daemon.core.protocol.IDownloadStateListener.Enqueued;
import com.aerofs.daemon.core.protocol.IDownloadStateListener.Ongoing;
import com.aerofs.daemon.core.protocol.IDownloadStateListener.Started;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.pb.PBTransferStateFormatter;
import com.aerofs.daemon.transport.tcpmt.TCP;
import com.aerofs.lib.KeyBasedThrottler;
import com.aerofs.lib.KeyBasedThrottler.Factory;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBNotification;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;

import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(KeyBasedThrottler.class)
@PowerMockIgnore({"ch.qos.logback.*", "org.slf4j.*"})
public class TestDownloadStateListener
{
    Logger l = Loggers.getLogger(TestDownloadStateListener.class);

    @Mock TCP _tcp;

    @Mock PBTransferStateFormatter _formatter;
    @Mock RitualNotificationServer _notifier;
    @Mock Factory _factory;
    @InjectMocks DownloadStateListener _listener;

    @Spy KeyBasedThrottler<SOCID> _throttler = new KeyBasedThrottler<SOCID>();

    OID _oid;
    DID _did;

    @Before public void setUp()
    {
        PowerMockito.mockStatic(System.class);

        when(_factory.<SOCID>create()).thenReturn(_throttler);

        _throttler.setDelay(1 * C.SEC);

        _listener.enableFilter(true);

        _oid = OID.generate();
        _did = DID.generate();
    }

    @After public void tearDown()
    {
        _throttler.clear();
    }

    @Test public void shouldFilterMeta()
    {
        _listener.stateChanged_(createSOCID(true), createOngoing(0, 100));
        verify(_notifier, never()).sendEvent_(any(PBNotification.class));
    }

    @Test public void shouldNotFilterMeta()
    {
        _listener.enableFilter(false);
        _listener.stateChanged_(createSOCID(true), createOngoing(0, 100));
        verify(_notifier, times(1)).sendEvent_(any(PBNotification.class));
    }

    @Test public void shouldFilterType()
    {
        when(System.currentTimeMillis()).thenReturn(1 * C.SEC, 2 * C.SEC);
        _listener.stateChanged_(createSOCID(false), Started.SINGLETON);
        _listener.stateChanged_(createSOCID(false), Enqueued.SINGLETON);
        verify(_notifier, never()).sendEvent_(any(PBNotification.class));
    }

    @Test public void shouldNotFilterType()
    {
        when(System.currentTimeMillis()).thenReturn(1 * C.SEC, 2 * C.SEC, 3 * C.SEC);
        _listener.stateChanged_(createSOCID(false), createOngoing(50, 100));
        _listener.stateChanged_(createSOCID(false), Ended.SINGLETON_OKAY);
        _listener.stateChanged_(createSOCID(false), Ended.SINGLETON_FAILED);
        verify(_notifier, times(3)).sendEvent_(any(PBNotification.class));
    }

    @Test public void shouldThrottle()
    {
        when(System.currentTimeMillis()).thenReturn(1L, 2L, 3L);
        _listener.stateChanged_(createSOCID(false), createOngoing(10, 100));
        _listener.stateChanged_(createSOCID(false), createOngoing(20, 100));
        _listener.stateChanged_(createSOCID(false), createOngoing(30, 100));
        verify(_notifier, times(1)).sendEvent_(any(PBNotification.class));
    }

    @Test public void shouldNotThrottle()
    {
        when(System.currentTimeMillis()).thenReturn(1 * C.SEC, 2 * C.SEC, 3 * C.SEC);
        _listener.stateChanged_(createSOCID(false), createOngoing(10, 100));
        _listener.stateChanged_(createSOCID(false), createOngoing(20, 100));
        _listener.stateChanged_(createSOCID(false), createOngoing(30, 100));
        verify(_notifier, times(3)).sendEvent_(any(PBNotification.class));
    }

    @Test public void shouldNotThrottleEnded()
    {
        when(System.currentTimeMillis()).thenReturn(1L, 2L);
        _listener.stateChanged_(createSOCID(false), createOngoing(10, 100));
        _listener.stateChanged_(createSOCID(false), Ended.SINGLETON_OKAY);
        verify(_notifier, times(2)).sendEvent_(any(PBNotification.class));
    }

    @Test public void shouldUntrack()
    {
        when(System.currentTimeMillis()).thenReturn(1L, 2L);
        _listener.stateChanged_(createSOCID(false), createOngoing(10, 100));
        _listener.stateChanged_(createSOCID(false), Ended.SINGLETON_OKAY);
        verify(_throttler, times(1)).untrack(any(SOCID.class));
    }

    SOCID createSOCID(boolean isMeta)
    {
        return new SOCID(new SIndex(0), _oid, isMeta ? CID.META : CID.CONTENT);
    }

    Ongoing createOngoing(long done, long total)
    {
        Endpoint ep = new Endpoint(_tcp, _did);
        return new Ongoing(ep, done, total);
    }
}
