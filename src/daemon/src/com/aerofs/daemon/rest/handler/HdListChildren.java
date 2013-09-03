package com.aerofs.daemon.rest.handler;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.RestObject;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.daemon.rest.util.AccessChecker;
import com.aerofs.lib.event.Prio;
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
    private final AccessChecker _access;
    private final DirectoryService _ds;
    private final IMapSIndex2SID _sidx2sid;

    @Inject
    public HdListChildren(AccessChecker access, DirectoryService ds, IMapSIndex2SID sidx2sid)
    {
        _access = access;
        _ds = ds;
        _sidx2sid = sidx2sid;
    }

    @Override
    protected void handleThrows_(EIListChildren ev, Prio prio) throws Exception
    {
        OA oa = _access.checkObjectFollowsAnchor_(ev._object, ev._user);

        if (!oa.isDir()) throw new ExNotFound();

        SIndex sidx = oa.soid().sidx();
        SID sid = _sidx2sid.get_(sidx);
        Collection<OID> children = _ds.getChildren_(oa.soid());

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
