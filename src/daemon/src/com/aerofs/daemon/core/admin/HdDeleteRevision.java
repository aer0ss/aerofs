/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.phy.IPhysicalRevProvider;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.event.admin.EIDeleteRevision;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.google.inject.Inject;

public class HdDeleteRevision extends AbstractHdIMC<EIDeleteRevision>
{
    private final IPhysicalRevProvider _prp;

    @Inject
    public HdDeleteRevision(IPhysicalStorage ps)
    {
        _prp = ps.getRevProvider();
    }

    @Override
    protected void handleThrows_(EIDeleteRevision ev) throws Exception
    {
        if (ev._index == null) {
            _prp.deleteAllRevisionsUnder_(ev._path);
        } else {
            _prp.deleteRevision_(ev._path, ev._index);
        }
    }
}
