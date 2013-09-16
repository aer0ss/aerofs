package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.event.fs.EIMoveObject;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdMoveObject extends AbstractHdIMC<EIMoveObject>
{
    private final DirectoryService _ds;
    private final TransManager _tm;
    private final ObjectMover _om;

    @Inject
    public HdMoveObject(ObjectMover om, TransManager tm, DirectoryService ds)
    {
        _om = om;
        _tm = tm;
        _ds = ds;
    }

    @Override
    protected void handleThrows_(EIMoveObject ev, Prio prio) throws Exception
    {
        SOID soid = _ds.resolveThrows_(ev._from);

        if (soid.oid().isTrash() || soid.oid().isRoot()) {
            throw new ExNoPerm("can't move system folders");
        }

        SOID soidToParent = _ds.resolveFollowAnchorThrows_(ev._toParent);

        Trans t = _tm.begin_();
        try {
            _om.move_(soid, soidToParent, ev._toName, PhysicalOp.APPLY, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
