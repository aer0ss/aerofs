package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.event.admin.EIGetACL;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdGetACL extends AbstractHdIMC<EIGetACL>
{
    private final LocalACL _lacl;

    @Inject
    public HdGetACL(LocalACL lacl)
    {
        this._lacl = lacl;
    }

    @Override
    protected void handleThrows_(EIGetACL ev, Prio prio)
            throws Exception
    {
        SOID soid = _lacl.checkThrows_(ev._user, ev._path, Role.VIEWER);

        ev.setResult_(_lacl.get_(soid.sidx()));
    }
}
