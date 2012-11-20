/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.IMultiplicityEventHandlerSetter;
import com.aerofs.daemon.event.admin.EITestMultiuserJoinRootStore;

import javax.inject.Inject;

class MultiuserEventHandlerSetter implements IMultiplicityEventHandlerSetter
{
    private final HdTestMultiuserJoinRootStore _hdTestMultiuserJoinRootStore;

    @Inject
    MultiuserEventHandlerSetter(HdTestMultiuserJoinRootStore hdTestMultiuserJoinRootStore)
    {
        _hdTestMultiuserJoinRootStore = hdTestMultiuserJoinRootStore;
    }

    @Override
    public void setHandlers_(CoreEventDispatcher dispatcher)
    {
        dispatcher
                .setHandler_(EITestMultiuserJoinRootStore.class, _hdTestMultiuserJoinRootStore);
    }
}
