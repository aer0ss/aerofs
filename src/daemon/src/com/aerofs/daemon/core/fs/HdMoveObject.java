package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.event.fs.EIMoveObject;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdMoveObject extends AbstractHdIMC<EIMoveObject>
{
    private final DirectoryService _ds;
    private final TransManager _tm;
    private final ImmigrantCreator _imc;

    @Inject
    public HdMoveObject(ImmigrantCreator imc, TransManager tm, DirectoryService ds)
    {
        _imc = imc;
        _tm = tm;
        _ds = ds;
    }

    @Override
    protected void handleThrows_(EIMoveObject ev) throws Exception
    {
        SOID soid = _ds.resolveThrows_(ev._from);

        if (soid.oid().isTrash() || soid.oid().isRoot()) {
            throw new ExNoPerm("can't move system folders");
        }

        SOID soidToParent = _ds.resolveFollowAnchorThrows_(ev._toParent);

        try (Trans t = _tm.begin_()) {
            _imc.move_(soid, soidToParent, ev._toName, PhysicalOp.APPLY, t);
            t.commit_();
        }
    }
}
