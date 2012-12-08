/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.event.admin.EITestMultiuserJoinRootStore;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.id.SID;

import javax.inject.Inject;

public class HdTestMultiuserJoinRootStore extends AbstractHdIMC<EITestMultiuserJoinRootStore>
{
    private final StoreCreator _sc;
    private final TransManager _tm;

    @Inject
    public HdTestMultiuserJoinRootStore(StoreCreator sc, TransManager tm)
    {
        _sc = sc;
        _tm = tm;
    }

    @Override
    protected void handleThrows_(EITestMultiuserJoinRootStore ev, Prio prio)
            throws Exception
    {
        SID sid = SID.rootSID(ev._user);

        Trans t = _tm.begin_();
        try {
            _sc.createRootStore_(sid, MultiuserPathResolver.getStorePath(sid), t);
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
