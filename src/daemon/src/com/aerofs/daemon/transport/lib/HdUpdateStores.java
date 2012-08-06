package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.EOUpdateStores;
import com.aerofs.daemon.lib.Prio;

class HdUpdateStores implements IEventHandler<EOUpdateStores> {

    private final ITransportImpl _tp;

    HdUpdateStores(ITransportImpl tp)
    {
        _tp = tp;
    }

    @Override
    public void handle_(EOUpdateStores ev, Prio prio)
    {
        _tp.updateStores_(ev._sidAdded, ev._sidRemoved);
    }
}
