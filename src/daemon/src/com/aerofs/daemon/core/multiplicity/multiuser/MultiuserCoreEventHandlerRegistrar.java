/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.event.admin.EITestMultiuserJoinRootStore;

import com.google.inject.Inject;

class MultiuserCoreEventHandlerRegistrar implements ICoreEventHandlerRegistrar
{
    private final HdTestMultiuserJoinRootStore _hdTestMultiuserJoinRootStore;

    @Inject
    MultiuserCoreEventHandlerRegistrar(HdTestMultiuserJoinRootStore hdTestMultiuserJoinRootStore)
    {
        _hdTestMultiuserJoinRootStore = hdTestMultiuserJoinRootStore;
    }

    @Override
    public void registerHandlers_(CoreEventDispatcher disp)
    {
        disp.setHandler_(EITestMultiuserJoinRootStore.class, _hdTestMultiuserJoinRootStore);
    }
}
