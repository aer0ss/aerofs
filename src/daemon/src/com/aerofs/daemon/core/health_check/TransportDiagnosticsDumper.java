/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.health_check;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.proto.Ritual.GetTransportDiagnosticsReply;
import com.google.inject.Inject;
import org.slf4j.Logger;

import static com.aerofs.lib.JsonFormat.prettyPrint;

final class TransportDiagnosticsDumper implements Runnable
{
    private static final Logger l = Loggers.getLogger(TransportDiagnosticsDumper.class);

    private final Transports _tps;

    @Inject
    TransportDiagnosticsDumper(Transports tps)
    {
        _tps = tps;
    }

    @Override
    public void run()
    {
        try {
            l.info("run tdd");
            dumpTransportDiagnostics();
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

        GetTransportDiagnosticsReply diagnostics = _tps.dumpDiagnostics();

        if (diagnostics.hasTcpDiagnostics()) {
            l.info("tcp:{}", prettyPrint(diagnostics.getTcpDiagnostics()));
        }
        if (diagnostics.hasJingleDiagnostics()) {
            l.info("jingle:{}", prettyPrint(diagnostics.getJingleDiagnostics()));
        }
        if (diagnostics.hasZephyrDiagnostics()) {
            l.info("zephyr:{}", prettyPrint(diagnostics.getZephyrDiagnostics()));
        }
    }
}
