/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;


import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.store.Stores;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import java.sql.SQLException;

/**
 * Device-centric reporting about remote updates (as opposed to (Store, Device)-centric reporting
 * as is offered by the Collectors)
 */
public class RemoteUpdates
{
    private final IStores _stores;
    private final MapSIndex2Store _sidx2store;

    @Inject
    public RemoteUpdates(Stores stores, MapSIndex2Store sidx2store)
    {
        _stores = stores;
        _sidx2store = sidx2store;
    }

    public boolean deviceHasUpdates_(DID did) throws SQLException
    {
        // TODO (MJ) iterating over all stores is slower than necessary
        // should consider adding a DB call to the Collector Filters Database
        for (SIndex sidx : _stores.getAll_()) {
            Store s = _sidx2store.get_(sidx);
            if (s.collector().hasUpdatesFrom_(did)) return true;
        }
        return false;
    }

}
