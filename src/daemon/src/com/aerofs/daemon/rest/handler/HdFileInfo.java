package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Role;
import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.Path;
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

    @Inject
    public HdFileInfo(DirectoryService ds, ACLChecker acl, IPhysicalStorage ps)
    {
        _ds = ds;
        _ps = ps;
        _acl = acl;
    }

    @Override
    protected void handleThrows_(EIFileInfo ev, Prio prio) throws Exception
    {
        SOID soid = _acl.checkNoFollowAnchorThrows_(ev._user, ev._path, Role.VIEWER);

        OA oa = _ds.getOAThrows_(soid);

        if (!oa.isFile()) throw new ExNotFile();

        ev.setResult_(file(ev._path, oa));
    }

    private static File file(Path path, OA oa)
    {
        String name = oa.name();
        long size = -1;
        Date last_modified = null;

        CA ca = oa.caMasterNullable();
        if (ca != null) {
            size = oa.caMaster().length();
            last_modified = new Date(oa.caMaster().mtime());
        }

        String parent = "/folders/" + path.removeLast().toStringRelative();

        return new File(name, parent, last_modified, size);
    }
}
