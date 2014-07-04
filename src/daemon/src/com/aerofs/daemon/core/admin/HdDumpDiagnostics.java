/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.admin.EIDumpDiagnostics;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Ritual.GetDiagnosticsReply;
import com.google.inject.Inject;

import static com.aerofs.daemon.core.tc.Cat.UNLIMITED;

public class HdDumpDiagnostics extends AbstractHdIMC<EIDumpDiagnostics>
{
    private final TokenManager _tokenManager;
    private final Devices _devices;
    private final Transports _tps;

    @Inject
    public HdDumpDiagnostics(TokenManager tokenManager, Devices devices, Transports tps)
    {
        _tokenManager = tokenManager;
        _devices = devices;
        _tps = tps;
    }

    @Override
    protected void handleThrows_(EIDumpDiagnostics ev, Prio prio) throws Exception
    {
        final GetDiagnosticsReply.Builder diagnosticsReplyBuilder = GetDiagnosticsReply.newBuilder();

        // start by getting the store availability diagnostics (within the core thread)

        diagnosticsReplyBuilder.setDeviceDiagnostics(_devices.dumpDiagnostics_());

        // then, get the transport diagnostics
        // NOTE: run this without the core lock held, because this method does
        // blocking calls like name resolution, etc.

        Token tk = _tokenManager.acquireThrows_(UNLIMITED, "transport diagnostics");
        try {
            TCB tcb = tk.pseudoPause_("transport diagnostics");
            try {
                diagnosticsReplyBuilder.setTransportDiagnostics(_tps.dumpDiagnostics_());
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }

        // now, set the result (all diagnostics collected)

        ev.setResult_(diagnosticsReplyBuilder.build());
    }
}
