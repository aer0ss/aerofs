package com.aerofs.daemon.rest.handler;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.util.AccessChecker;
import com.aerofs.rest.api.File;
import com.aerofs.daemon.rest.event.EIFileInfo;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.Date;

public class HdFileInfo extends AbstractHdIMC<EIFileInfo>
{
    private final AccessChecker _access;

    @Inject
    public HdFileInfo(AccessChecker access)
    {
        _access = access;
    }

    @Override
    protected void handleThrows_(EIFileInfo ev, Prio prio) throws ExNotFound, SQLException
    {
        OA oa = _access.checkObject_(ev._object, ev._user);

        if (!oa.isFile()) throw new ExNotFound();

        ev.setResult_(file(ev._object.toStringFormal(), oa));
    }

    private static File file(String id, OA oa)
    {
        String name = oa.name();
        long size = -1;
        Date last_modified = null;

        CA ca = oa.caMasterNullable();
        if (ca != null) {
            size = ca.length();
            last_modified = new Date(ca.mtime());
        }

        return new File(name, id, last_modified, size);
    }
}
