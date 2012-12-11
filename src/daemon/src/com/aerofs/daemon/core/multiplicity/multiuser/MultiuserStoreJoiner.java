/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

public class MultiuserStoreJoiner implements IStoreJoiner
{
    private final StoreCreator _sc;

    @Inject
    public MultiuserStoreJoiner(StoreCreator sc)
    {
        _sc = sc;
    }

    @Override
    public void joinStore_(SIndex sidx, SID sid, String folderName, Trans t) throws Exception
    {
        if (sid.isRoot()) _sc.createRootStore_(sid, MultiuserPathResolver.getStorePath(sid), t);
    }

    @Override
    public void leaveStore_(SIndex sidx, SID sid, Trans t) throws Exception
    {
        if (sid.isRoot()) {
            // TODO:
        }
    }
}
