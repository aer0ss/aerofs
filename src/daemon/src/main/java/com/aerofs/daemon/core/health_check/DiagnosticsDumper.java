/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.health_check;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.daemon.lib.IDiagnosable;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgVer;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.os.OSUtil;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.protobuf.Message;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import static com.aerofs.lib.JsonFormat.prettyPrint;

/**
 * Dumps core and transport diagnositcs whenever {@link DiagnosticsDumper#run()}
 * is called. This class is executed on a fixed schedule <strong>outside</strong>
 * the core lock by {@link com.aerofs.daemon.core.health_check.HealthCheckService}.
 *
 * @see com.aerofs.daemon.core.health_check.HealthCheckService
 */
public final class DiagnosticsDumper implements HealthCheckService.ScheduledRunnable
{
    private static final long MAX_DEVICE_DIAGNOSTICS_WAIT_TIME = 2 * C.SEC;

    private static final Logger l = Loggers.getLogger(DiagnosticsDumper.class);

    private final String _newline = System.getProperty("line.separator");
    private final Object _locker = new Object();
    private final CoreQueue _q;
    private final Devices _devices;
    private final Transports _tps;
    private final UploadState _ul;
    private final DownloadState _dl;
    private final CfgLocalDID _cfgLocalDID;
    private final CfgLocalUser _cfgLocalUser;
    private final CfgVer _cfgVer;

    @Inject
    DiagnosticsDumper(CoreQueue q, Devices devices, Transports tps, UploadState ul,
                      DownloadState dl, CfgLocalDID cfgLocalDID, CfgLocalUser cfgLocalUser,
                      CfgVer cfgVer)
    {
        _q = q;
        _devices = devices;
        _tps = tps;
        _ul = ul;
        _dl = dl;
        _cfgLocalDID = cfgLocalDID;
        _cfgLocalUser = cfgLocalUser;
        _cfgVer = cfgVer;
    }

    @Override
    public long interval()
    {
        return 10 * C.MIN;
    }

    @Override
    public void run()
    {
        try {
            l.info("run dd");

            StringBuilder builder = new StringBuilder(1024);
            builder.append(_newline);

            dumpSystemInformation(builder);
            dumpDiagnostics(builder, "uls", _ul); // runs holding the core lock
            dumpDiagnostics(builder, "dls", _dl); // runs holding the core lock
            dumpDiagnostics(builder, "devices", _devices); // runs holding the core lock
            dumpTransportDiagnostics(builder); // runs without holding core lock

            l.info("diagnostics:{}", builder.toString());
        } catch (Throwable t) {
            l.error("fail dump diagnostics", t);
        }
    }

    private void dumpSystemInformation(StringBuilder builder)
    {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");

        builder.append("did:").append(_cfgLocalDID.get()).append(_newline);
        builder.append("usr:").append(_cfgLocalUser.get()).append(_newline);
        builder.append("ver:").append(_cfgVer.get()).append(_newline);
        builder.append("os :").append(OSUtil.getOSName()).append(_newline);
        builder.append("tz :").append(TimeZone.getDefault().getDisplayName()).append(_newline);
        builder.append("now:").append(dateFormat.format(new Date())).append(_newline);
    }

    private void dumpDiagnostics(StringBuilder builder, String componentName, IDiagnosable component)
    {
        Message diagnostics = getDiagnostics(builder, component);

        if (diagnostics != null) {
            builder.append(componentName).append(":").append(prettyPrint(diagnostics)).append(
                    _newline);
        } else {
            builder.append(componentName).append(":failed diagnostics dump").append(_newline);
        }
    }

    private @Nullable Message getDiagnostics(final StringBuilder builder, final IDiagnosable component)
    {
        final AtomicReference<Message> diagnostics = new AtomicReference<>(null);

        _q.enqueueBlocking(new AbstractEBSelfHandling() {
            @Override
            public void handle_() {
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
                    builder.append("interrupted during wait for diagnostics dump from ").append(component).append(
                            _newline);
                }
            }
        }

        return diagnostics.get();
    }

    private void dumpTransportDiagnostics(StringBuilder builder)
    {
        if (!_tps.started()) {
            builder.append("transports not started").append(_newline);
            return;
        }

        Message transportDiagnostics = _tps.dumpDiagnostics_();
        builder.append("transports:").append(prettyPrint(transportDiagnostics)).append(_newline);

        builder.append("transfer:").append(new Gson().toJson(ImmutableMap.of(
                "in", _tps.bytesIn(),
                "out", _tps.bytesOut()
        )));
    }
}
