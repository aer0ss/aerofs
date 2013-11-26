package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.admin.EIUpdateACL;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExNotShared;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdUpdateACL extends AbstractHdIMC<EIUpdateACL>
{
    private final ACLSynchronizer _aclsync;
    private final DirectoryService _ds;

    @Inject
    public HdUpdateACL(ACLSynchronizer aclsync, DirectoryService ds)
    {
        _aclsync = aclsync;
        _ds = ds;
    }

    @Override
    protected void handleThrows_(EIUpdateACL ev, Prio prio)
            throws Exception
    {
        // Don't check ACL here. SP Servlet will check it for us.
        SOID soid = _ds.resolveThrows_(ev._path);
        OA oa = _ds.getOA_(soid);
        if (oa.isAnchor()) soid = _ds.followAnchorThrows_(oa);

        if (!soid.oid().isRoot()) throw new ExNotShared();

        _aclsync.update_(soid.sidx(), ev._subject, ev._permissions, ev._suppressSharedFolderRulesWarnings);
    }
}
