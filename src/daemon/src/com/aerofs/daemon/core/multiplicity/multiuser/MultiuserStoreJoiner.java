/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.ObjectSurgeon;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.AbstractStoreJoiner;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.id.SID;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public class MultiuserStoreJoiner extends AbstractStoreJoiner
{
    private final CfgRootSID _cfgRootSID;
    private final IStores _stores;
    private final StoreCreator _sc;
    private final StoreDeleter _sd;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public MultiuserStoreJoiner(CfgRootSID cfgRootSID, IStores stores,
            IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx,
            StoreCreator sc, StoreDeleter sd, DirectoryService ds,
            ObjectCreator oc, ObjectDeleter od, ObjectSurgeon os)
    {
        super(ds, os, oc, od);
        _cfgRootSID = cfgRootSID;
        _stores = stores;
        _sc = sc;
        _sd = sd;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
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

        l.info("join {} {} {}", sidx, sid, folderName);
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
            _sd.deleteRootStore_(sidx, PhysicalOp.APPLY, t);
        }
    }

    @Override
    public void adjustAnchors_(SIndex sidx, String folderName, Set<UserID> newMembers, Trans t)
            throws Exception
    {
        SID sid = _sidx2sid.get_(sidx);
        checkArgument(!sid.isUserRoot());
        for (UserID user : newMembers) {
            SIndex root = _sid2sidx.getNullable_(SID.rootSID(user));
            if (root != null) createAnchorIfNeeded_(sidx, sid, folderName, root, t);
        }
    }
}
