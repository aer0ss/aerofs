package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.net.link.LinkStateMonitor;
import com.aerofs.daemon.event.admin.EIPauseOrResumeSyncing;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.google.inject.Inject;

public class HdPauseOrResumeSyncing extends AbstractHdIMC<EIPauseOrResumeSyncing>
{
    private final LinkStateMonitor _lsm;

    @Inject
    public HdPauseOrResumeSyncing(LinkStateMonitor lsm)
    {
        _lsm = lsm;
    }

    @Override
    protected void handleThrows_(EIPauseOrResumeSyncing ev, Prio prio)
            throws Exception
    {
        if (ev._pause) _lsm.markLinksDown_();
        else _lsm.markLinksUp_();
    }
}
