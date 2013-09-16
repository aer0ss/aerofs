package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.fs.EISetAttr;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdSetAttr extends AbstractHdIMC<EISetAttr>
{
    private final DirectoryService _ds;
    private final TransManager _tm;
    private final VersionUpdater _vu;

    @Inject
    public HdSetAttr(VersionUpdater vu, TransManager tm, DirectoryService ds)
    {
        _vu = vu;
        _tm = tm;
        _ds = ds;
    }

    @Override
    protected void handleThrows_(EISetAttr ev, Prio prio) throws Exception
    {
        SOID soid = _ds.resolveFollowAnchorThrows_(ev._path);

        OA oa = _ds.getOA_(soid);
        if (ev._flags == null || oa.flags() == ev._flags) return;

        final Trans t = _tm.begin_();
        try {
            _ds.setOAFlags_(soid, ev._flags, t);

            _vu.update_(new SOCKID(soid, CID.META), t);
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
