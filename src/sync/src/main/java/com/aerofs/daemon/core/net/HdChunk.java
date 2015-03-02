package com.aerofs.daemon.core.net;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.rx.EIChunk;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

/**
 * Handler for {@link EIChunk} events
 */
public class HdChunk implements IEventHandler<EIChunk>
{
    private final UnicastInputOutputStack _stack;

    @Inject
    public HdChunk(UnicastInputOutputStack stack)
    {
        _stack = stack;
    }

    @Override
    public void handle_(EIChunk ev, Prio prio)
    {
        PeerContext pc = new PeerContext(ev._ep, ev._userID);
        RawMessage r = new RawMessage(ev.is(), ev.wireLength());
        _stack.input().onStreamChunkReceived_(ev._streamId, ev._seq, r, pc);
    }
}
