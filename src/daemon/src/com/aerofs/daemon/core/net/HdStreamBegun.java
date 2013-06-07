package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.rx.EIStreamBegun;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

/**
 * Handler for the {@link EIStreamBegun} event
 */
public class HdStreamBegun implements IEventHandler<EIStreamBegun>
{
    private final UnicastInputOutputStack _stack;

    @Inject
    public HdStreamBegun(UnicastInputOutputStack stack)
    {
        _stack = stack;
    }

    @Override
    public void handle_(EIStreamBegun ev, Prio prio)
    {
        PeerContext pc = new PeerContext(ev._ep);
        RawMessage r = new RawMessage(ev.is(), ev.wireLength());
        _stack.input().onStreamBegun_(ev._streamId, r, pc);
    }
}
