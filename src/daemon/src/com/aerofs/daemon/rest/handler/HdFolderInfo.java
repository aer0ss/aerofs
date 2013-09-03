package com.aerofs.daemon.rest.handler;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.event.EIFolderInfo;
import com.aerofs.daemon.rest.util.AccessChecker;
import com.aerofs.lib.event.Prio;
import com.aerofs.rest.api.Folder;
import com.google.inject.Inject;

import java.sql.SQLException;

public class HdFolderInfo extends AbstractHdIMC<EIFolderInfo>
{
    private final AccessChecker _access;

    @Inject
    public HdFolderInfo(AccessChecker access)
    {
        _access = access;
    }

    @Override
    protected void handleThrows_(EIFolderInfo ev, Prio prio) throws ExNotFound, SQLException
    {
        OA oa = _access.checkObject_(ev._object, ev._user);

        if (!oa.isDirOrAnchor()) throw new ExNotFound();

        ev.setResult_(new Folder(oa.name(), ev._object.toStringFormal(), oa.isAnchor()));
    }
}
