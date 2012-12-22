package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.event.admin.EIDeleteACL;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExNotShared;
import com.aerofs.lib.id.SOID;
import com.aerofs.base.id.UserID;
import com.aerofs.sv.client.SVClient;
import com.aerofs.proto.Sv.PBSVEvent.Type;
import com.google.inject.Inject;

public class HdDeleteACL extends AbstractHdIMC<EIDeleteACL>
{
    private final LocalACL _lacl;
    private final ACLSynchronizer _aclsync;

    @Inject
    public HdDeleteACL(LocalACL lacl, ACLSynchronizer aclsync)
    {
        _lacl = lacl;
        _aclsync = aclsync;
    }

    @Override
    protected void handleThrows_(EIDeleteACL ev, Prio prio)
            throws Exception
    {
        SOID soid = _lacl.checkThrows_(ev._user, ev._path, Role.OWNER);
        if (!soid.oid().isRoot()) throw new ExNotShared();

        // log kickouts
        for (UserID subject : ev._subjects) {
            SVClient.sendEventAsync(Type.KICKOUT, subject.toString());
        }

        _aclsync.delete_(soid.sidx(), ev._subjects);
    }
}
