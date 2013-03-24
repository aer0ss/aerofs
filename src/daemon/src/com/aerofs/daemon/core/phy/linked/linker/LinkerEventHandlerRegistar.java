/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.core.phy.linked.linker.event.EIMightCreateNotification;
import com.aerofs.daemon.core.phy.linked.linker.event.EIMightDeleteNotification;
import com.aerofs.daemon.core.phy.linked.linker.event.EITestPauseOrResumeLinker;
import com.google.inject.Inject;

public class LinkerEventHandlerRegistar implements ICoreEventHandlerRegistrar
{
    private final HdMightCreateNotification _hdMightCreate;
    private final HdMightDeleteNotification _hdMightDelete;
    private final HdTestPauseOrResumeLinker _hdTestPauseResume;

    @Inject
    public LinkerEventHandlerRegistar(HdTestPauseOrResumeLinker pauseResume,
            HdMightDeleteNotification mdn, HdMightCreateNotification mcn)
    {
        _hdMightCreate = mcn;
        _hdMightDelete = mdn;
        _hdTestPauseResume = pauseResume;
    }

    @Override
    public void registerHandlers_(CoreEventDispatcher disp)
    {
        disp
                .setHandler_(EIMightCreateNotification.class, _hdMightCreate)
                .setHandler_(EIMightDeleteNotification.class, _hdMightDelete)
                .setHandler_(EITestPauseOrResumeLinker.class, _hdTestPauseResume);
    }
}
