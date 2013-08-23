package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Role;
import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.rest.event.EIDeleteFile;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExNotFile;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdDeleteFile extends AbstractHdIMC<EIDeleteFile>
{
    private final DirectoryService _ds;
    private final ACLChecker _acl;
    private final ObjectDeleter _od;
    private final TransManager _tm;

    @Inject
    public HdDeleteFile(DirectoryService ds, ACLChecker acl, ObjectDeleter od, TransManager tm)
    {
        _ds = ds;
        _acl = acl;
        _od = od;
        _tm = tm;
    }

    @Override
    protected void handleThrows_(EIDeleteFile ev, Prio prio) throws Exception
    {
        if (ev._path.isEmpty()) throw new ExNotFile();

        SOID soid = _acl.checkNoFollowAnchorThrows_(ev._user, ev._path, Role.EDITOR);
        OA oa = _ds.getOAThrows_(soid);

        if (!oa.isFile()) throw new ExNotFile();

        Trans t = _tm.begin_();
        try {
            _od.delete_(soid, PhysicalOp.APPLY, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
