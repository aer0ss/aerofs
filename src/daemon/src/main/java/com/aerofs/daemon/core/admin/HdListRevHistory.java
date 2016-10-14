package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.event.admin.EIListRevHistory;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.google.inject.Inject;

public class HdListRevHistory extends AbstractHdIMC<EIListRevHistory> {

    private final IPhysicalStorage _ps;

    @Inject
    public HdListRevHistory(IPhysicalStorage ps)
    {
        _ps = ps;
    }

    @Override
    protected void handleThrows_(EIListRevHistory ev)
            throws Exception
    {
        ev.setResult_(_ps.getRevProvider().listRevHistory_(ev.getPath()));
    }
}
