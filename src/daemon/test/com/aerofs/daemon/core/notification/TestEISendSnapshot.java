/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.net.IUploadStateListener.Key;
import com.aerofs.daemon.core.net.IUploadStateListener.Value;
import com.aerofs.daemon.core.net.UploadState;
import com.aerofs.daemon.core.protocol.DownloadState;
import com.aerofs.daemon.core.protocol.IDownloadStateListener.State;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.pb.PBTransferStateFormatter;
import com.aerofs.daemon.transport.tcpmt.TCP;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.InetSocketAddress;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TestEISendSnapshot
{
    @Spy DownloadState _dls = new DownloadState();
    @Spy UploadState _uls = new UploadState();

    @Mock RitualNotificationServer _rns;
    @Mock InetSocketAddress _to;
    @Mock PathStatusNotifier _psn;
    @Mock PBTransferStateFormatter _formatter;
    @InjectMocks EISendSnapshot _handler;

    @Mock TCP _tcp;

    @Before
    public void setup()
    {
        _handler.enableFilter(true);
    }

    @Test
    public void shouldFilterUploadMeta()
    {
        _uls.progress_(createSOCID(true), createEndpoint(), 0, 100);
        _handler.handle_();
        verify(_formatter, never()).formatUploadState(any(Key.class), any(Value.class));
    }

    @Test
    public void shouldNotFilterUploadMeta()
    {
        _handler.enableFilter(false);
        _uls.progress_(createSOCID(true), createEndpoint(), 0, 100);
        _handler.handle_();
        verify(_formatter).formatUploadState(any(Key.class), any(Value.class));
    }

    @Test
    public void shouldNotFilterUploadContent()
    {
        _uls.progress_(createSOCID(false), createEndpoint(), 0, 100);
        _handler.handle_();
        verify(_formatter).formatUploadState(any(Key.class), any(Value.class));
    }

    @Test
    public void shouldFilterDownloadMeta()
    {
        SOCID socid = createSOCID(true);
        Endpoint ep = createEndpoint();

        _dls.enqueued_(socid);
        _handler.handle_();
        _dls.started_(socid);
        _handler.handle_();
        _dls.ongoing_(socid, ep, 30, 100);
        _handler.handle_();
        _dls.ended_(socid, true);
        _handler.handle_();
        verify(_formatter, never()).formatDownloadState(any(SOCID.class), any(State.class));
    }

    @Test
    public void shouldNotFilterDownloadMeta()
    {
        SOCID socid = createSOCID(true);
        Endpoint ep = createEndpoint();

        _handler.enableFilter(false);
        _dls.enqueued_(socid);
        _handler.handle_();
        _dls.started_(socid);
        _handler.handle_();
        _dls.ongoing_(socid, ep, 30, 100);
        _handler.handle_();
        verify(_formatter, times(3)).formatDownloadState(any(SOCID.class), any(State.class));
    }

    @Test
    public void shouldNotFilterDownloadContent()
    {
        SOCID socid = createSOCID(false);
        Endpoint ep = createEndpoint();

        _handler.enableFilter(false);
        _dls.enqueued_(socid);
        _dls.started_(socid);
        _dls.ongoing_(socid, ep, 30, 100);
        _handler.handle_();
        verify(_formatter).formatDownloadState(any(SOCID.class), any(State.class));
    }

    @Test
    public void shouldFilterStartedAndEnqueuedDownloads()
    {
        SOCID socid = createSOCID(false);

        _dls.enqueued_(socid);
        _handler.handle_();
        _dls.started_(socid);
        _handler.handle_();
        _dls.ended_(socid, false);
        _handler.handle_();
        verify(_formatter, never()).formatDownloadState(any(SOCID.class), any(State.class));
    }

    @Test
    public void shouldNotFilterStartedAndEnqueuedDownloads()
    {
        SOCID socid = createSOCID(false);

        _handler.enableFilter(false);
        _dls.enqueued_(socid);
        _handler.handle_();
        _dls.started_(socid);
        _handler.handle_();
        verify(_formatter, times(2)).formatDownloadState(any(SOCID.class), any(State.class));
    }

    private SOCID createSOCID(boolean isMeta)
    {
        return new SOCID(new SIndex(0), OID.generate(), isMeta ? CID.META : CID.CONTENT);
    }

    private Endpoint createEndpoint()
    {
        return new Endpoint(_tcp, DID.generate());
    }
}
