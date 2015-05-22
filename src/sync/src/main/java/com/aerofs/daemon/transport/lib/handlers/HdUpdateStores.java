package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.EOUpdateStores;
import com.aerofs.daemon.transport.lib.IStores;
import com.aerofs.lib.event.Prio;

public class HdUpdateStores implements IEventHandler<EOUpdateStores> {

    private final IStores stores;

    public HdUpdateStores(IStores stores)
    {
        this.stores = stores;
    }

    @Override
    public void handle_(EOUpdateStores ev, Prio prio)
    {
        stores.updateStores(ev._sidsAdded, ev._sidsRemoved);
    }
}
