/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.daemon.core.online_status.OnlineStatusNotifier;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferProgress;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferredItem;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.tcp.TCP;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class TestEISendSnapshot
{
    @Spy DownloadState _dls = new DownloadState();
    @Mock DownloadNotifier _dn;
    @Spy UploadState _uls = new UploadState();
    @Mock UploadNotifier _un;
    @Mock PathStatusNotifier _psn;
    @Mock OnlineStatusNotifier _osn;
    @Mock TCP _tcp;

    private EISendSnapshot createHandler(boolean filterMeta)
    {
        return new EISendSnapshot(_dls, _dn, _uls, _un,
                ImmutableSet.<ISnapshotableNotificationEmitter>of(_psn, _osn), filterMeta);
    }

    private SOCID createSOCID(boolean isMeta)
    {
        return new SOCID(new SIndex(0), OID.generate(), isMeta ? CID.META : CID.CONTENT);
    }

    private Endpoint createEndpoint()
    {
        return new Endpoint(_tcp, DID.generate());
    }

    @Test
    public void shouldFilterUploadMeta()
    {
        _uls.progress_(createSOCID(true), createEndpoint(), 0, 100);

        EISendSnapshot handler = createHandler(true);
        handler.handle_();

        verify(_un, never()).sendTransferNotification_(any(TransferredItem.class), any(TransferProgress.class));
    }

    @Test
    public void shouldNotFilterUploadMeta()
    {
        _uls.progress_(createSOCID(true), createEndpoint(), 0, 100);

        EISendSnapshot handler = createHandler(false);
        handler.handle_();

        verify(_un).sendTransferNotification_(any(TransferredItem.class), any(TransferProgress.class));
    }

    @Test
    public void shouldNotFilterUploadContent()
    {
        _uls.progress_(createSOCID(false), createEndpoint(), 0, 100);

        EISendSnapshot handler = createHandler(true);
        handler.handle_();

        verify(_un).sendTransferNotification_(any(TransferredItem.class), any(TransferProgress.class));
    }


    @Test
    public void shouldFilterDownloadMeta()
    {
        _dls.progress_(createSOCID(true), createEndpoint(), 0, 100);

        EISendSnapshot handler = createHandler(true);
        handler.handle_();

        verify(_dn, never()).sendTransferNotification_(any(TransferredItem.class), any(TransferProgress.class));
    }

    @Test
    public void shouldNotFilterDownloadMeta()
    {
        _dls.progress_(createSOCID(true), createEndpoint(), 0, 100);

        EISendSnapshot handler = createHandler(false);
        handler.handle_();

        verify(_dn).sendTransferNotification_(any(TransferredItem.class), any(TransferProgress.class));
    }

    @Test
    public void shouldNotFilterDownloadContent()
    {
        _dls.progress_(createSOCID(false), createEndpoint(), 0, 100);

        EISendSnapshot handler = createHandler(true);
        handler.handle_();

        verify(_dn).sendTransferNotification_(any(TransferredItem.class), any(TransferProgress.class));
    }

    @Test
    public void shouldSendOnlineStatus()
    {
        EISendSnapshot handler = createHandler(true);
        handler.handle_();

        verify(_osn).sendOnlineStatusNotification();
    }
}
