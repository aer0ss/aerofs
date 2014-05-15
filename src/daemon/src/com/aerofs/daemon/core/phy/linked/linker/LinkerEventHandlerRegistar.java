/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.core.phy.linked.linker.event.EIMightCreateNotification;
import com.aerofs.daemon.core.phy.linked.linker.event.EIMightDeleteNotification;
import com.aerofs.daemon.core.phy.linked.linker.event.EITestPauseOrResumeLinker;
import com.aerofs.daemon.event.fs.EICreateRoot;
import com.aerofs.daemon.event.fs.EILinkRoot;
import com.aerofs.daemon.event.fs.EIListPendingRoots;
import com.aerofs.daemon.event.fs.EIUnlinkRoot;
import com.google.inject.Inject;

public class LinkerEventHandlerRegistar implements ICoreEventHandlerRegistrar
{
    private final HdCreateRoot _hdCreateRoot;
    private final HdLinkRoot _hdLinkRoot;
    private final HdListPendingRoots _hdListPendingRoots;
    private final HdMightCreateNotification _hdMightCreate;
    private final HdMightDeleteNotification _hdMightDelete;
    private final HdTestPauseOrResumeLinker _hdTestPauseResume;
    private final HdUnlinkRoot _hdUnlinkRoot;

    @Inject
    public LinkerEventHandlerRegistar(HdCreateRoot hdCreateRoot,  HdLinkRoot hdLinkRoot,
            HdListPendingRoots hdListPendingRoots, HdTestPauseOrResumeLinker pauseResume,
            HdMightCreateNotification mcn, HdMightDeleteNotification mdn,
            HdUnlinkRoot hdUnlinkRoot)
    {
        _hdCreateRoot = hdCreateRoot;
        _hdLinkRoot = hdLinkRoot;
        _hdListPendingRoots = hdListPendingRoots;
        _hdMightCreate = mcn;
        _hdMightDelete = mdn;
        _hdTestPauseResume = pauseResume;
        _hdUnlinkRoot = hdUnlinkRoot;
    }

    @Override
    public void registerHandlers_(CoreEventDispatcher disp)
    {
        disp
                .setHandler_(EICreateRoot.class, _hdCreateRoot)
                .setHandler_(EILinkRoot.class, _hdLinkRoot)
                .setHandler_(EIListPendingRoots.class, _hdListPendingRoots)
                .setHandler_(EIMightCreateNotification.class, _hdMightCreate)
                .setHandler_(EIMightDeleteNotification.class, _hdMightDelete)
                .setHandler_(EITestPauseOrResumeLinker.class, _hdTestPauseResume)
                .setHandler_(EIUnlinkRoot.class, _hdUnlinkRoot);
    }
}
