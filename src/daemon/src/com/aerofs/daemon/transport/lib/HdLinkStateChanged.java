package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.event.net.EOLinkStateChanged;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.ex.ExNoResource;

class HdLinkStateChanged extends AbstractHdIMC<EOLinkStateChanged>
{

    private final ITransportImpl _tp;

    HdLinkStateChanged(ITransportImpl tp)
    {
        _tp = tp;
    }

    @Override
    protected void handleThrows_(EOLinkStateChanged ev, Prio prio)
        throws ExNoResource
    {
        _tp.linkStateChanged_(ev._removed, ev._added, ev._prev, ev._current);
    }

}
