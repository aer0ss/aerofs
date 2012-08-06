package com.aerofs.daemon.core.net.rx;

import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.net.PeerContext;
import com.aerofs.daemon.core.net.RawMessage;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

/**
 * Handler for the {@link EIUnicastMessage} event
 */
public class HdUnicastMessage implements IEventHandler<EIUnicastMessage>
{
    private final UnicastInputOutputStack _stack;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public HdUnicastMessage(UnicastInputOutputStack stack, IMapSID2SIndex sid2sidx)
    {
        _stack = stack;
        _sid2sidx = sid2sidx;
    }

    @Override
    public void handle_(EIUnicastMessage ev, Prio prio)
    {
        SIndex sidx = _sid2sidx.getNullable_(ev._sid);
        if (sidx == null) {
            Util.l(this).info("no store " + ev._sid);
        } else {
            PeerContext pc = new PeerContext(ev._ep, sidx);
            RawMessage r = new RawMessage(ev.is(), ev.wireLength());
            _stack.input().onUnicastDatagramReceived_(r, pc);
        }
    }
}
