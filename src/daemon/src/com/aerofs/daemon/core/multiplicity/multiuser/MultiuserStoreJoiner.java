/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.id.SID;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

public class MultiuserStoreJoiner implements IStoreJoiner
{
    private final CfgRootSID _cfgRootSID;
    private final IStores _stores;
    private final StoreCreator _sc;
    private final StoreDeleter _sd;
    private final IMapSIndex2SID _sidx2sid;

    @Inject
    public MultiuserStoreJoiner(CfgRootSID cfgRootSID, IStores stores,
            StoreCreator sc, StoreDeleter sd, IMapSIndex2SID sidx2sid)
    {
        _cfgRootSID = cfgRootSID;
        _stores = stores;
        _sc = sc;
        _sd = sd;
        _sidx2sid = sidx2sid;
    }

    @Override
    public void joinStore_(SIndex sidx, SID sid, String folderName, boolean external, Trans t)
            throws Exception
    {
        // sigh... we create a root store for TeamServer clients to simplify the server-side
        // code so we have to explicitly ignore it here because:
        // 1. this store will always be empty, might as well not waste DB space
        // 2. this store may confuse some storage backends
        if (sid.equals(_cfgRootSID.get())) return;

        // every store is a root store, until it is referenced by an anchor in another store
        _sc.createRootStore_(sid, folderName, t);
    }

    @Override
    public void leaveStore_(SIndex sidx, SID sid, Trans t) throws Exception
    {
        // NB: we do not immediately delete non-root stores to intermediate states with broken
        // store hiearchy ("dangling pointer"-style). By virtue of the implicit refcount in the
        // store hierarchy they will automatically be deleted when all anchors pointing to them
        // disappear
        if (_sidx2sid.getNullable_(sidx) != null && _stores.isRoot_(sidx)) {
            _sd.deleteRootStore_(sidx, t);
        }
    }
}
