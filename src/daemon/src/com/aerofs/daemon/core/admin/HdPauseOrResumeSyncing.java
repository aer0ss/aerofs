package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.net.link.LinkStateService;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.admin.EIPauseOrResumeSyncing;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

public class HdPauseOrResumeSyncing extends AbstractHdIMC<EIPauseOrResumeSyncing>
{
    private final TC _tc;
    private final LinkStateService _lss;

    @Inject
    public HdPauseOrResumeSyncing(TC tc, LinkStateService lss)
    {
        _tc = tc;
        _lss = lss;
    }

    @Override
    protected void handleThrows_(EIPauseOrResumeSyncing ev, Prio prio) throws Exception
    {
        if (ev._pause) {
            _lss.markLinksDown_();
        } else {
            Token tk = _tc.acquire_(Cat.UNLIMITED, "iface-up");
            try {
                _lss.markLinksUp_(tk);
            } finally {
                tk.reclaim_();
            }
        }
    }
}
