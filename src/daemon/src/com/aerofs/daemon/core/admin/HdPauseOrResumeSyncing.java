package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.admin.EIPauseOrResumeSyncing;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

public class HdPauseOrResumeSyncing extends AbstractHdIMC<EIPauseOrResumeSyncing>
{
    private final TokenManager _tokenManager;
    private final LinkStateService _lss;

    @Inject
    public HdPauseOrResumeSyncing(TokenManager tokenManager, LinkStateService lss)
    {
        _tokenManager = tokenManager;
        _lss = lss;
    }

    @Override
    protected void handleThrows_(EIPauseOrResumeSyncing ev, Prio prio) throws Exception
    {
        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "pause-sync");
        try {
            TCB tcb = tk.pseudoPause_("lss-trigger");
            try {
                if (ev._pause) {
                    _lss.markLinksDown();
                } else {
                    _lss.markLinksUp();
                }
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }
}
