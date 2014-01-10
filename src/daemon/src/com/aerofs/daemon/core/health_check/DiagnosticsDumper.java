/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.health_check;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Diagnostics.DeviceDiagnostics;
import com.aerofs.proto.Diagnostics.TransportDiagnostics;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

import static com.aerofs.lib.JsonFormat.prettyPrint;

final class DiagnosticsDumper implements Runnable
{
    private static final long MAX_DEVICE_DIAGNOSTICS_WAIT_TIME = 2 * C.SEC;

    private static final Logger l = Loggers.getLogger(DiagnosticsDumper.class);

    private final Object _locker = new Object();
    private final CoreQueue _q;
    private final DevicePresence _dp;
    private final Transports _tps;

    @Inject
    DiagnosticsDumper(CoreQueue q, DevicePresence dp, Transports tps)
    {
        _q = q;
        _dp = dp;
        _tps = tps;
    }

    @Override
    public void run()
    {
        try {
            l.info("run dd");
            dumpTransportDiagnostics();
            dumpDeviceDiagnostics();
        } catch (Throwable t) {
            l.error("fail dump diagnostics", t);
        }
    }

    private void dumpTransportDiagnostics()
    {
        if (!_tps.started()) {
            l.warn("transports not started");
            return;
        }

        TransportDiagnostics transportDiagnostics = _tps.dumpDiagnostics();

        if (transportDiagnostics.hasTcpDiagnostics()) {
            l.info("tcp:{}", prettyPrint(transportDiagnostics.getTcpDiagnostics()));
        }
        if (transportDiagnostics.hasJingleDiagnostics()) {
            l.info("jingle:{}", prettyPrint(transportDiagnostics.getJingleDiagnostics()));
        }
        if (transportDiagnostics.hasZephyrDiagnostics()) {
            l.info("zephyr:{}", prettyPrint(transportDiagnostics.getZephyrDiagnostics()));
        }
    }

    private void dumpDeviceDiagnostics()
    {
        DeviceDiagnostics deviceDiagnostics = getDeviceDiagnostics();

        if (deviceDiagnostics != null) {
            l.info("devices:{}", prettyPrint(deviceDiagnostics));
        } else {
            l.warn("devices: failed diagnostics dump");
        }
    }

    private @Nullable DeviceDiagnostics getDeviceDiagnostics()
    {
        final AtomicReference<DeviceDiagnostics> deviceDiagnostics = new AtomicReference<DeviceDiagnostics>(null);

        _q.enqueueBlocking(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                synchronized (_locker) {
                    deviceDiagnostics.set(_dp.dumpDiagnostics());
                    _locker.notifyAll();
                }
            }
        }, Prio.LO);

        synchronized (_locker) {
            if(deviceDiagnostics.get() == null) {
                try {
                    _locker.wait(MAX_DEVICE_DIAGNOSTICS_WAIT_TIME);
                } catch (InterruptedException e) {
                    l.warn("interrupted during wait for device diagnostics dump");
                }
            }
        }

        return deviceDiagnostics.get();
    }
}
