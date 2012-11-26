package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.rx.EIStreamAborted;
import com.aerofs.daemon.lib.Prio;
import com.google.inject.Inject;

public class HdStreamAborted implements IEventHandler<EIStreamAborted>
{
    private final UnicastInputOutputStack _stack;

    @Inject
    public HdStreamAborted(UnicastInputOutputStack stack)
    {
        _stack = stack;
    }

    @Override
    public void handle_(EIStreamAborted ev, Prio prio)
    {
        _stack.input().onStreamAborted_(ev.sid(), ev.ep(), ev.reason());
    }
}
