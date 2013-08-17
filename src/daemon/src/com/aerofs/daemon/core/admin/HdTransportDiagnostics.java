/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.admin.EITransportDiagnostics;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

import static com.aerofs.daemon.core.tc.Cat.UNLIMITED;

public class HdTransportDiagnostics extends AbstractHdIMC<EITransportDiagnostics>
{
    private final TC _tc;
    private final Transports _tps;

    @Inject
    public HdTransportDiagnostics(TC tc, Transports tps)
    {
        _tc = tc;
        _tps = tps;
    }

    @Override
    protected void handleThrows_(EITransportDiagnostics ev, Prio prio) throws Exception
    {
        // NOTE: run this without the core lock held, because this method does
        // blocking calls like name resolution, etc.

        Token tk = _tc.acquireThrows_(UNLIMITED, "transport diagnostics");
        try {
            TCB tcb = tk.pseudoPause_("transport diagnostics");
            try {
                ev.setResult_(_tps.dumpDiagnostics());
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }
}
