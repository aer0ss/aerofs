package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Role;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExNotDir;
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

    @Inject
    HdListChildren(DirectoryService ds, ACLChecker acl)
    {
        _ds = ds;
        _acl = acl;
    }

    @Override
    protected void handleThrows_(EIListChildren ev, Prio prio) throws Exception
    {
        SOID soid = _acl.checkThrows_(ev._user, ev._path, Role.VIEWER);
        OA oa = _ds.getOAThrows_(soid);

        if (oa.isExpelled()) throw new ExExpelled();
        if (oa.isFile()) throw new ExNotDir();

        Collection<OID> children = _ds.getChildren_(soid);

        List<Folder> folders = Lists.newArrayList();
        List<File> files = Lists.newArrayList();
        for (OID c : children) {
            OA coa = _ds.getOAThrows_(new SOID(soid.sidx(), c));
            if (coa.isExpelled()) continue;
            if (coa.isFile()) {
                long size = -1;
                Date lastModified = null;
                if (coa.caMasterNullable() != null) {
                    size = coa.caMaster().length();
                    lastModified = new Date(coa.caMaster().mtime());
                }
                files.add(new File(coa.name(), ev._path.toStringRelative(), lastModified, size));
            } else {
                folders.add(new Folder(coa.name(), ev._path.toStringRelative(), coa.isAnchor()));
            }
        }

        ev.setResult_(new Listing(folders, files));
    }
}
