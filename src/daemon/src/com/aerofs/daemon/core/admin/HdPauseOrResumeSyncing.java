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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HdPauseOrResumeSyncing extends AbstractHdIMC<EIPauseOrResumeSyncing>
{
    private static final Logger l = LoggerFactory.getLogger(HdPauseOrResumeSyncing.class);

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
        l.info(ev._pause ? "pause syncing" : "resume syncing");

        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "pause-sync");
        try {
            TCB tcb = tk.pseudoPause_("lss-trigger");
            try {
                if (ev._pause) {
                    l.debug("initiating pause sync");

                    _lss.markLinksDown();

                    l.debug("completed pause sync");
                } else {
                    l.debug("initiating resume sync");

                    _lss.markLinksUp();

                    l.debug("completed resume sync");
                }
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }
}
