package com.aerofs.daemon.core.net;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

/**
 * Handler for the {@link EIUnicastMessage} event
 */
public class HdUnicastMessage implements IEventHandler<EIUnicastMessage>
{
    private final UnicastInputOutputStack _stack;

    @Inject
    public HdUnicastMessage(UnicastInputOutputStack stack)
    {
        _stack = stack;
    }

    @Override
    public void handle_(EIUnicastMessage ev, Prio prio)
    {
        PeerContext pc = new PeerContext(ev._ep, ev._userID);
        RawMessage r = new RawMessage(ev.is(), ev.wireLength());
        _stack.input().onUnicastDatagramReceived_(r, pc);
    }
}
