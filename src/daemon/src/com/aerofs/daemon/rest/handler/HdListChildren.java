package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.RestObject;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.lib.Path;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.rest.api.File;
import com.aerofs.rest.api.Folder;
import com.aerofs.rest.api.Listing;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public class HdListChildren extends AbstractHdIMC<EIListChildren>
{
    private final ACLChecker _acl;
    private final DirectoryService _ds;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    HdListChildren(DirectoryService ds, ACLChecker acl, IMapSID2SIndex sid2sidx)
    {
        _ds = ds;
        _acl = acl;
        _sid2sidx = sid2sidx;
    }

    @Override
    protected void handleThrows_(EIListChildren ev, Prio prio) throws Exception
    {
        SID sid = ev._object.sid;
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) throw new ExNotFound();

        SOID soid = new SOID(sidx, ev._object.oid);
        OA oa = _ds.getOAThrows_(soid);

        if (oa.isExpelled()) throw new ExExpelled();
        if (oa.isFile()) throw new ExNotDir();

        if (oa.isAnchor()) {
            soid = _ds.followAnchorThrows_(oa);
            sidx = soid.sidx();
            sid = SID.anchorOID2storeSID(ev._object.oid);
        }

        _acl.checkThrows_(ev._user, sidx, Role.VIEWER);
        Collection<OID> children = _ds.getChildren_(soid);

        List<Folder> folders = Lists.newArrayList();
        List<File> files = Lists.newArrayList();
        for (OID c : children) {
            OA coa = _ds.getOAThrows_(new SOID(sidx, c));
            if (coa.isExpelled()) continue;
            String restId = new RestObject(sid, c).toStringFormal();
            if (coa.isFile()) {
                long size = -1;
                Date lastModified = null;
                if (coa.caMasterNullable() != null) {
                    size = coa.caMaster().length();
                    lastModified = new Date(coa.caMaster().mtime());
                }
                files.add(new File(coa.name(), restId, lastModified, size));
            } else {
                folders.add(new Folder(coa.name(), restId, coa.isAnchor()));
            }
        }

        ev.setResult_(new Listing(folders, files));
    }
}
