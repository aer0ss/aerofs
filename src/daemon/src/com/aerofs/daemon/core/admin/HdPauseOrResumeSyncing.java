package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.net.link.LinkStateService;
import com.aerofs.daemon.event.admin.EIPauseOrResumeSyncing;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.google.inject.Inject;

public class HdPauseOrResumeSyncing extends AbstractHdIMC<EIPauseOrResumeSyncing>
{
    private final LinkStateService _lss;

    @Inject
    public HdPauseOrResumeSyncing(LinkStateService lss)
    {
        _lss = lss;
    }

    @Override
    protected void handleThrows_(EIPauseOrResumeSyncing ev, Prio prio)
            throws Exception
    {
        if (ev._pause) _lss.markLinksDown_();
        else _lss.markLinksUp_();
    }
}
