package com.aerofs.daemon.transport.lib;

import com.aerofs.base.ex.ExNoResource;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.event.net.EOLinkStateChanged;
import com.aerofs.lib.event.Prio;

class HdLinkStateChanged extends AbstractHdIMC<EOLinkStateChanged>
{
    private final ILinkStateListener _tp;

    HdLinkStateChanged(ILinkStateListener tp)
    {
        _tp = tp;
    }

    @Override
    protected void handleThrows_(EOLinkStateChanged ev, Prio prio)
        throws ExNoResource
    {
        _tp.linkStateChanged(ev._removed, ev._added, ev._prev, ev._current);
    }
}
