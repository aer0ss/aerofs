package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.tx.EOTxEndStream;
import com.aerofs.lib.event.Prio;

public class HdTxEndStream implements IEventHandler<EOTxEndStream> {

    private final StreamManager _sm;

    public HdTxEndStream(ITransportImpl tp)
    {
        _sm = tp.sm();
    }

    @Override
    public void handle_(EOTxEndStream ev, Prio prio)
    {
        _sm.removeOutgoingStream(ev._streamId);
    }
}
