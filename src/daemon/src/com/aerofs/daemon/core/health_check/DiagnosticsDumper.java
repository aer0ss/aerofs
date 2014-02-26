/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.health_check;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.net.TransferStatisticsManager;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.daemon.lib.IDiagnosable;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.Prio;
import com.aerofs.metriks.IMetriks;
import com.aerofs.proto.Diagnostics.TransportTransfer;
import com.aerofs.proto.Diagnostics.TransportTransferDiagnostics;
import com.google.inject.Inject;
import com.google.protobuf.Message;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

import static com.aerofs.lib.JsonFormat.prettyPrint;

/**
 * Dumps core and transport diagnositcs whenever {@link DiagnosticsDumper#run()}
 * is called. This class is executed on a fixed schedule <strong>outside</strong>
 * the core lock by {@link com.aerofs.daemon.core.health_check.HealthCheckService}.
 *
 * @see com.aerofs.daemon.core.health_check.HealthCheckService
 */
final class DiagnosticsDumper implements Runnable
{
    private static final long MAX_DEVICE_DIAGNOSTICS_WAIT_TIME = 2 * C.SEC;

    private static final Logger l = Loggers.getLogger(DiagnosticsDumper.class);

    private final Object _locker = new Object();
    private final CoreQueue _q;
    private final DevicePresence _dp;
    private final TransferStatisticsManager _tsm;
    private final Transports _tps;
    private final UploadState _ul;
    private final DownloadState _dl;
    private final IMetriks _metriks;

    @Inject
    DiagnosticsDumper(CoreQueue q, DevicePresence dp, TransferStatisticsManager tsm, Transports tps, UploadState ul, DownloadState dl, IMetriks metriks)
    {
        _q = q;
        _dp = dp;
        _tsm = tsm;
        _tps = tps;
        _ul = ul;
        _dl = dl;
        _metriks = metriks;
    }

    @Override
    public void run()
    {
        try {
            l.info("run dd");
            dumpDiagnostics("uls", _ul); // runs holding the core lock
            dumpDiagnostics("dls", _dl); // runs holding the core lock
            dumpDiagnostics("devices", _dp); // runs holding the core lock
            dumpTransportDiagnostics(); // runs without holding core lock
            dumpTransportTransferDiagnostics(); // runs without holding core lock
        } catch (Throwable t) {
            l.error("fail dump diagnostics", t);
        }
    }

    private void dumpDiagnostics(String componentName, IDiagnosable component)
    {
        Message diagnostics = getDiagnostics(component);

        if (diagnostics != null) {
            l.info("{}:{}", componentName, prettyPrint(diagnostics));
        } else {
            l.warn("{}: failed diagnostics dump", componentName);
        }
    }

    private @Nullable Message getDiagnostics(final IDiagnosable component)
    {
        final AtomicReference<Message> diagnostics = new AtomicReference<Message>(null);

        _q.enqueueBlocking(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                synchronized (_locker) {
                    diagnostics.set(component.dumpDiagnostics_());
                    _locker.notifyAll();
                }
            }
        }, Prio.LO);

        synchronized (_locker) {
            if(diagnostics.get() == null) {
                try {
                    _locker.wait(MAX_DEVICE_DIAGNOSTICS_WAIT_TIME);
                } catch (InterruptedException e) {
                    l.warn("interrupted during wait for diagnostics dump from {}", component);
                }
            }
        }

        return diagnostics.get();
    }

    private void dumpTransportDiagnostics()
    {
        if (!_tps.started()) {
            l.warn("transports not started");
            return;
        }

        Message transportDiagnostics = _tps.dumpDiagnostics_();
        l.info("transports:{}", prettyPrint(transportDiagnostics));
    }

    private void dumpTransportTransferDiagnostics()
    {
        // print
        TransportTransferDiagnostics transferDiagnostics = _tsm.getAndReset();
        l.info("transfer:{}", prettyPrint(transferDiagnostics));

        // send
        // FIXME (AG): add an method called "addAll" that automatically adds all fields
        l.info("send transfer");
        for (TransportTransfer transfer : transferDiagnostics.getTransferList()) {
            _metriks.newMetrik("transfer")
                    .addField("transport_id", transfer.getTransportId())
                    .addField("bytes_transferred", transfer.getBytesTransferred())
                    .addField("bytes_errored", transfer.getBytesErrored())
                    .send();
        }
    }
}
