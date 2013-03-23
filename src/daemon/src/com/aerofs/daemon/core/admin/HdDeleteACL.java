package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.event.admin.EIDeleteACL;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.acl.Role;
import com.aerofs.daemon.core.ex.ExNotShared;
import com.aerofs.lib.id.SOID;
import com.aerofs.sv.client.SVClient;
import com.aerofs.proto.Sv.PBSVEvent.Type;
import com.google.inject.Inject;

public class HdDeleteACL extends AbstractHdIMC<EIDeleteACL>
{
    private final ACLChecker _acl;
    private final ACLSynchronizer _aclsync;

    @Inject
    public HdDeleteACL(ACLChecker acl, ACLSynchronizer aclsync)
    {
        _acl = acl;
        _aclsync = aclsync;
    }

    @Override
    protected void handleThrows_(EIDeleteACL ev, Prio prio)
            throws Exception
    {
        SOID soid = _acl.checkThrows_(ev._user, ev._path, Role.OWNER);
        if (!soid.oid().isRoot()) throw new ExNotShared();

        SVClient.sendEventAsync(Type.KICKOUT, ev._subject.getString());

        _aclsync.delete_(soid.sidx(), ev._subject);
    }
}
