/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.net.ITransferStateListener.Key;
import com.aerofs.daemon.core.net.ITransferStateListener.Value;
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

    @Spy KeyBasedThrottler<Key> _throttler = new KeyBasedThrottler<Key>();

    OID _oid;
    DID _did;

    @Before public void setUp()
    {
        PowerMockito.mockStatic(System.class);

        when(_factory.<Key>create()).thenReturn(_throttler);

        _throttler.setDelay(1 * C.SEC);

        _listener.enableFilter(true);

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
        verify(_notifier, never()).sendEvent_(any(PBNotification.class));
    }

    @Test public void shouldNotFilterMeta()
    {
        _listener.enableFilter(false);
        invoke(true, 50, 100);
        verify(_notifier, times(1)).sendEvent_(any(PBNotification.class));
    }

    @Test public void shouldThrottle()
    {
        when(System.currentTimeMillis()).thenReturn(10L, 100L, 200L, 300L);
        invoke(false, 10, 100);
        invoke(false, 20, 100);
        invoke(false, 30, 100);
        invoke(false, 40, 100);
        verify(_notifier, times(1)).sendEvent_(any(PBNotification.class));
    }

    @Test public void shouldNotThrottle()
    {
        when(System.currentTimeMillis()).thenReturn(1 * C.SEC, 10 * C.SEC, 20 * C.SEC, 30 * C.SEC);
        invoke(false, 10, 100);
        invoke(false, 20, 100);
        invoke(false, 30, 100);
        invoke(false, 40, 100);
        verify(_notifier, times(4)).sendEvent_(any(PBNotification.class));
    }

    @Test public void shouldNotThrottleCompleted()
    {
        when(System.currentTimeMillis()).thenReturn(10L, 100L);
        invoke(false, 10, 100);
        invoke(false, 100, 100);
        verify(_notifier, times(2)).sendEvent_(any(PBNotification.class));
    }

    @Test public void shouldUntrack()
    {
        when(System.currentTimeMillis()).thenReturn(10L, 100L);
        invoke(false, 10, 100);
        invoke(false, 100, 100);
        verify(_throttler, times(1)).untrack(any(Key.class));
    }

    void invoke(boolean isMeta, long done, long total)
    {
        SOCID socid = new SOCID(new SIndex(0), _oid, isMeta ? CID.META : CID.CONTENT);
        Endpoint ep = new Endpoint(_tcp, _did);
        Key key = new Key(socid, ep);
        Value value = new Value(done, total);

        _listener.stateChanged_(key, value);
    }
}
