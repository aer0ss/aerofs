package com.aerofs.daemon.core.fs;

import java.util.ArrayList;
import java.util.List;

import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.fs.EIGetChildrenAttr;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Role;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;
import com.google.inject.Inject;

public class HdGetChildrenAttr extends AbstractHdIMC<EIGetChildrenAttr>
{
    private final LocalACL _lacl;
    private final DirectoryService _ds;

    @Inject
    public HdGetChildrenAttr(DirectoryService ds, LocalACL lacl)
    {
        _ds = ds;
        _lacl = lacl;
    }

    @Override
    protected void handleThrows_(EIGetChildrenAttr ev, Prio prio) throws Exception
    {
        ev.setResult_(getChildrenAttr_(ev.user(), ev._path));
    }

    private List<OA> getChildrenAttr_(String user, Path path)
        throws Exception
    {
        SOID soid = _lacl.checkThrows_(user, path, Role.VIEWER);

        ArrayList<OA> oas = new ArrayList<OA>();
        for (OID oidChild : _ds.getChildren_(soid)) {
            SOID soidChild = new SOID(soid.sidx(), oidChild);
            if (oidChild.isTrash()) continue;
            oas.add(_ds.getOANullable_(soidChild));
        }

        return oas;
    }
}
