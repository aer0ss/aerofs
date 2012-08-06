package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.event.fs.EICreateObject;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.Role;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdCreateObject extends AbstractHdIMC<EICreateObject>
{
    private DirectoryService _ds;
    private LocalACL _lacl;
    private TransManager _tm;
    private ObjectCreator _oc;

    @Inject
    public void inject_(DirectoryService ds, LocalACL lacl, TransManager tm, ObjectCreator oc)
    {
        _ds = ds;
        _lacl = lacl;
        _tm = tm;
        _oc = oc;
    }

    @Override
    public void handleThrows_(EICreateObject ev, Prio prio) throws Exception
    {
        SOID soidExist = _ds.resolveNullable_(ev._path);
        if (soidExist != null) {
            ev.setResult_(true);
            return;
        }

        if (ev._path.isEmpty()) throw new ExBadArgs("cannot create root folder");

        // need read_attr right to read ACL.
        Path pathParent = ev._path.removeLast();
        SOID soidParent = _lacl.checkThrows_(ev.user(), pathParent, Role.EDITOR);

        Trans t = _tm.begin_();
        try {
            OA.Type type = ev._dir ? OA.Type.DIR : OA.Type.FILE;
            _oc.create_(type, soidParent, ev._path.last(), PhysicalOp.APPLY, t);
            t.commit_();
        } finally {
            t.end_();
        }

        ev.setResult_(false);
    }
}
