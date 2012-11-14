package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.event.fs.EIDeleteObject;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;
import com.google.inject.Inject;

public class HdDeleteObject extends AbstractHdIMC<EIDeleteObject>
{
    private LocalACL _lacl;
    private TransManager _tm;
    private ObjectDeleter _od;

    @Inject
    public void inject_(ObjectDeleter od, TransManager tm, LocalACL lacl)
    {
        _lacl = lacl;
        _od = od;
        _tm = tm;
    }

    @Override
    protected void handleThrows_(EIDeleteObject ev, Prio prio) throws Exception
    {
        if (ev._path.isEmpty()) {
            throw new ExNoPerm("removing root folder");
        }

        Path path = ev._path;
        SOID soid = _lacl.checkNoFollowAnchorThrows_(ev.user(), path, Role.EDITOR);

        Trans t = _tm.begin_();
        try {
            _od.delete_(soid, PhysicalOp.APPLY, null, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
