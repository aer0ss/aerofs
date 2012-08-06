package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.rx.EORxEndStream;
import com.aerofs.daemon.lib.Prio;

public class HdRxEndStream implements IEventHandler<EORxEndStream> {

    private final StreamManager _sm;

    public HdRxEndStream(ITransportImpl tp)
    {
        _sm = tp.sm();
    }

    @Override
    public void handle_(EORxEndStream ev, Prio prio)
    {
        _sm.removeIncomingStream(ev._did, ev._sid);
    }
}
