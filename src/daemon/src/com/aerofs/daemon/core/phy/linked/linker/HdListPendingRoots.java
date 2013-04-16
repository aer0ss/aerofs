/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.event.fs.EIListPendingRoots;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.PendingRootDatabase;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

public class HdListPendingRoots extends AbstractHdIMC<EIListPendingRoots>
{
    private final PendingRootDatabase _prdb;

    @Inject
    public HdListPendingRoots(PendingRootDatabase prdb)
    {
        _prdb = prdb;
    }

    @Override
    protected void handleThrows_(EIListPendingRoots ev, Prio prio) throws Exception
    {
        ev.setResult(_prdb.getPendingRoots());
    }
}
