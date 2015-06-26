/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.event.fs.EIListUnlinkedRoots;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.UnlinkedRootDatabase;
import com.google.inject.Inject;

public class HdListUnlinkedRoots extends AbstractHdIMC<EIListUnlinkedRoots>
{
    private final UnlinkedRootDatabase _urdb;

    @Inject
    public HdListUnlinkedRoots(UnlinkedRootDatabase urdb)
    {
        _urdb = urdb;
    }

    @Override
    protected void handleThrows_(EIListUnlinkedRoots ev) throws Exception
    {
        ev.setResult(_urdb.getUnlinkedRoots());
    }
}
