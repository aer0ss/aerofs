/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.core.phy.linked.linker.event.EITestPauseOrResumeLinker;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

class HdTestPauseOrResumeLinker extends AbstractHdIMC<EITestPauseOrResumeLinker>
{
    private final Linker _linker;
    private final HdMightCreateNotification _hdMightCreate;
    private final HdMightDeleteNotification _hdMightDelete;

    @Inject
    public HdTestPauseOrResumeLinker(Linker linker,
            HdMightCreateNotification mcn, HdMightDeleteNotification mdn)
    {
        _linker = linker;
        _hdMightCreate = mcn;
        _hdMightDelete = mdn;
    }

    @Override
    protected void handleThrows_(EITestPauseOrResumeLinker ev, Prio prio)
            throws Exception
    {
        l.debug(ev._pause ? "paused" : "resumed");

        _hdMightCreate.setDisabled(ev._pause);
        _hdMightDelete.setDisabled(ev._pause);

        if (!ev._pause) {
            _linker.fullScan_();
        }
    }
}
