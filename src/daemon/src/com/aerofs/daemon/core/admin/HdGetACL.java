package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.event.admin.EIGetACL;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UserID;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.inject.Inject;

import java.util.Map.Entry;

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

        // skip team server ids
        Builder<UserID, Role> builder = ImmutableMap.builder();
        for (Entry<UserID, Role> en : _lacl.get_(soid.sidx()).entrySet()) {
            if (en.getKey().isTeamServerID()) continue;
            builder.put(en.getKey(), en.getValue());
        }

        ev.setResult_(builder.build());
    }
}
