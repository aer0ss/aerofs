package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SIndex;
import com.aerofs.rest.api.File;
import com.aerofs.daemon.rest.event.EIFileInfo;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExNotFile;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import java.util.Date;

public class HdFileInfo extends AbstractHdIMC<EIFileInfo>
{
    private final ACLChecker _acl;
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public HdFileInfo(DirectoryService ds, ACLChecker acl, IPhysicalStorage ps,
            IMapSID2SIndex sid2sidx)
    {
        _ds = ds;
        _ps = ps;
        _acl = acl;
        _sid2sidx = sid2sidx;
    }

    @Override
    protected void handleThrows_(EIFileInfo ev, Prio prio) throws Exception
    {
        SID sid = ev._object.sid;
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) throw new ExNotFound();

        SOID soid = new SOID(sidx, ev._object.oid);
        _acl.checkThrows_(ev._user, soid.sidx(), Role.VIEWER);

        OA oa = _ds.getOAThrows_(soid);

        if (!oa.isFile()) throw new ExNotFile();

        ev.setResult_(file(ev._object.toStringFormal(), oa));
    }

    private static File file(String id, OA oa)
    {
        String name = oa.name();
        long size = -1;
        Date last_modified = null;

        CA ca = oa.caMasterNullable();
        if (ca != null) {
            size = oa.caMaster().length();
            last_modified = new Date(oa.caMaster().mtime());
        }

        return new File(name, id, last_modified, size);
    }
}
