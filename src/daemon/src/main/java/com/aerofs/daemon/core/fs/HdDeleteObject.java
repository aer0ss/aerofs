package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.event.fs.EIDeleteObject;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdDeleteObject extends AbstractHdIMC<EIDeleteObject>
{
    private final DirectoryService _ds;
    private final TransManager _tm;
    private final ObjectDeleter _od;

    @Inject
    public HdDeleteObject(ObjectDeleter od, TransManager tm, DirectoryService ds)
    {
        _ds = ds;
        _od = od;
        _tm = tm;
    }

    @Override
    protected void handleThrows_(EIDeleteObject ev) throws Exception
    {
        if (ev._path.isEmpty()) {
            throw new ExNoPerm("removing root folder");
        }

        // do not follow anchor
        SOID soid = _ds.resolveThrows_(ev._path);

        try (Trans t = _tm.begin_()) {
            _od.delete_(soid, PhysicalOp.APPLY, t);
            t.commit_();
        }
    }
}
