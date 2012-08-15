package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.ComMonitor;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.fs.EISetAttr;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Role;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdSetAttr extends AbstractHdIMC<EISetAttr>
{
    private final LocalACL _lacl;
    private final DirectoryService _ds;
    private final TransManager _tm;
    private final ComMonitor _cm;

    @Inject
    public HdSetAttr(ComMonitor cm, TransManager tm, DirectoryService ds, LocalACL lacl)
    {
        _cm = cm;
        _tm = tm;
        _ds = ds;
        _lacl = lacl;
    }

    @Override
    protected void handleThrows_(EISetAttr ev, Prio prio) throws Exception
    {
        SOID soid = _lacl.checkThrows_(ev.user(), ev._path, Role.EDITOR);

        OA oa = _ds.getOA_(soid);
        boolean flg = ev._flags != null && oa.flags() != ev._flags;

        if (!flg) return;

        final Trans t = _tm.begin_();
        try {
            if (flg) _ds.setOAFlags_(soid, ev._flags, t);

            _cm.atomicWrite_(new SOCKID(soid, CID.META), t);
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
