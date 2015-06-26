package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.event.admin.EIListRevChildren;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.google.inject.Inject;

public class HdListRevChildren extends AbstractHdIMC<EIListRevChildren> {

    private final IPhysicalStorage _ps;

    @Inject
    public HdListRevChildren(IPhysicalStorage ps)
    {
        _ps = ps;
    }

    @Override
    protected void handleThrows_(EIListRevChildren ev)
            throws Exception
    {
        ev.setResult_(_ps.getRevProvider().listRevChildren_(ev.getPath()));
    }
}
