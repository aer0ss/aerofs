package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.fs.EIGetAttr;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdGetAttr extends AbstractHdIMC<EIGetAttr>
{

    private final DirectoryService _ds;
    private final ACLChecker _acl;

    @Inject
    public HdGetAttr(ACLChecker acl, DirectoryService ds)
    {
        _acl = acl;
        _ds = ds;
    }

    @Override
    public void handleThrows_(EIGetAttr ev, Prio prio) throws Exception
    {
        SOID soid = _ds.resolveNullable_(ev._path);
        if (soid == null) {
            ev.setResult_(null);

        } else {
            _acl.checkThrows_(ev.user(), soid.sidx(), Role.VIEWER);
            OA oa = _ds.getOANullable_(soid);
            // oa may be null
            ev.setResult_(oa);
        }
    }
}
