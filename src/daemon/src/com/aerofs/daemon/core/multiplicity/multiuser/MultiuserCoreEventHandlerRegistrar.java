/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.core.admin.HdExportAll;
import com.aerofs.daemon.event.admin.EIExportAll;
import com.aerofs.daemon.event.admin.EITestMultiuserJoinRootStore;

import com.google.inject.Inject;

class MultiuserCoreEventHandlerRegistrar implements ICoreEventHandlerRegistrar
{
    private final HdTestMultiuserJoinRootStore _hdTestMultiuserJoinRootStore;
    private final HdExportAll _hdExportAll;

    @Inject
    MultiuserCoreEventHandlerRegistrar(HdTestMultiuserJoinRootStore hdTestMultiuserJoinRootStore,
            HdExportAll hdExportAll)
    {
        _hdTestMultiuserJoinRootStore = hdTestMultiuserJoinRootStore;
        _hdExportAll = hdExportAll;
    }

    @Override
    public void registerHandlers_(CoreEventDispatcher disp)
    {
        disp.setHandler_(EITestMultiuserJoinRootStore.class, _hdTestMultiuserJoinRootStore)
            .setHandler_(EIExportAll.class, _hdExportAll);
    }
}
