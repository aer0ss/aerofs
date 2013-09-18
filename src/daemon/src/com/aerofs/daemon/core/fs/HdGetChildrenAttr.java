package com.aerofs.daemon.core.fs;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.fs.EIGetChildrenAttr;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.base.id.OID;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;
import com.google.inject.Inject;

public class HdGetChildrenAttr extends AbstractHdIMC<EIGetChildrenAttr>
{
    private final DirectoryService _ds;

    @Inject
    public HdGetChildrenAttr(DirectoryService ds)
    {
        _ds = ds;
    }

    @Override
    protected void handleThrows_(EIGetChildrenAttr ev, Prio prio) throws Exception
    {
        ev.setResult_(getChildrenAttr_(ev._path));
    }

    private List<OA> getChildrenAttr_(Path path)
        throws Exception
    {
        SOID soid = _ds.resolveFollowAnchorThrows_(path);

        return getChildrenAttrImpl_(soid, _ds);
    }

    /**
     * This is the implementation shared by RitualService, MobileService, and RestService
     */
    public static List<OA> getChildrenAttrImpl_(SOID soid, DirectoryService ds)
            throws SQLException, ExNotDir, ExNotFound
    {
        ArrayList<OA> oas = new ArrayList<OA>();
        for (OID oidChild : ds.getChildren_(soid)) {
            SOID soidChild = new SOID(soid.sidx(), oidChild);
            if (oidChild.isTrash()) continue;
            oas.add(ds.getOANullable_(soidChild));
        }

        return oas;
    }
}
