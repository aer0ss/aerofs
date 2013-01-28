package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.rx.EISessionEnded;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

public class HdSessionEnded implements IEventHandler<EISessionEnded>
{
    private final UnicastInputOutputStack _stack;

    @Inject
    public HdSessionEnded(UnicastInputOutputStack stack)
    {
        _stack = stack;
    }

    @Override
    public void handle_(EISessionEnded ev, Prio prio)
    {
        _stack.input().sessionEnded_(ev.ep(), ev.outbound(), ev.inbound());
    }

}
