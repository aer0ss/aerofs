package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.event.EIFolderInfo;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SOID;
import com.aerofs.rest.api.Folder;
import com.google.inject.Inject;

public class HdFolderInfo extends AbstractHdIMC<EIFolderInfo>
{
    private final ACLChecker _acl;
    private final DirectoryService _ds;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public HdFolderInfo(DirectoryService ds, ACLChecker acl, IMapSID2SIndex sid2sidx)
    {
        _ds = ds;
        _acl = acl;
        _sid2sidx = sid2sidx;
    }

    @Override
    protected void handleThrows_(EIFolderInfo ev, Prio prio) throws Exception
    {
        SID sid = ev._object.sid;
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) throw new ExNotFound();

        SOID soid = new SOID(sidx, ev._object.oid);
        _acl.checkThrows_(ev._user, soid.sidx(), Role.VIEWER);

        OA oa = _ds.getOAThrows_(soid);

        if (!oa.isDirOrAnchor()) throw new ExNotDir();

        ev.setResult_(new Folder(oa.name(), ev._object.toStringFormal(), oa.isAnchor()));
    }
}
