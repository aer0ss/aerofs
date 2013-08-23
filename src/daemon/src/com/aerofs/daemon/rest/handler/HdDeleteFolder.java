package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.rest.event.EIDeleteFolder;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdDeleteFolder extends AbstractHdIMC<EIDeleteFolder>
{
    private final DirectoryService _ds;
    private final ACLChecker _acl;
    private final ObjectDeleter _od;
    private final TransManager _tm;

    @Inject
    public HdDeleteFolder(DirectoryService ds, ACLChecker acl, ObjectDeleter od, TransManager tm)
    {
        _ds = ds;
        _acl = acl;
        _od = od;
        _tm = tm;
    }

    @Override
    protected void handleThrows_(EIDeleteFolder ev, Prio prio) throws Exception
    {
        if (ev._path.isEmpty()) {
            throw new ExNoPerm("removing root folder");
        }

        SOID soid = _acl.checkNoFollowAnchorThrows_(ev._user, ev._path, Role.EDITOR);
        OA oa = _ds.getOAThrows_(soid);

        if (oa.isFile()) throw new ExNotDir();

        if (!ev._recurse && _ds.getChildren_(soid).size() > 0) {
            throw new ExBadArgs("Folder not empty");
        }

        Trans t = _tm.begin_();
        try {
            _od.delete_(soid, PhysicalOp.APPLY, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
