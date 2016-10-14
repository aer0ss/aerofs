package com.aerofs.daemon.core.fs;

import java.sql.SQLException;
import java.util.List;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.fs.EIGetChildrenAttr;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.ids.OID;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;
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
    protected void handleThrows_(EIGetChildrenAttr ev) throws Exception
    {
        SOID soid = _ds.resolveFollowAnchorThrows_(ev._path);
        ev.setResult_(getChildrenAttrImpl_(soid, _ds));
    }

    /**
     * This is the implementation shared by RitualService, MobileService, and RestService
     */
    public static List<OA> getChildrenAttrImpl_(SOID soid, DirectoryService ds)
            throws SQLException, ExNotDir, ExNotFound
    {
        OA oa = ds.getOAThrows_(soid);
        if (!oa.isDir()) throw new ExNotDir();

        List<OA> oas = Lists.newArrayList();
        for (OID oidChild : ds.getChildren_(soid)) {
            SOID soidChild = new SOID(soid.sidx(), oidChild);
            if (oidChild.isTrash()) continue;
            oas.add(ds.getOANullable_(soidChild));
        }

        return oas;
    }
}
