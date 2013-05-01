/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.tc.CoreLockReleasingExecutor;
import com.aerofs.daemon.event.admin.EIExportConflict;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SOID;

import javax.inject.Inject;
import java.io.File;

public class HdExportConflict extends AbstractHdExport<EIExportConflict>
{
    private final DirectoryService _ds;

    @Inject
    public HdExportConflict(CoreLockReleasingExecutor clre, DirectoryService ds)
    {
        super(clre);
        _ds = ds;
    }

    @Override
    protected void handleThrows_(EIExportConflict ev, Prio prio) throws Exception
    {
        SOID soid = _ds.resolveThrows_(ev._path);
        OA oa = _ds.getOAThrows_(soid);
        CA ca = oa.caThrows(ev._kidx);
        IPhysicalFile pf = ca.physicalFile();

        File dst = createTempFileWithSameExtension(oa.name());

        exportOrDeleteDest_(pf.newInputStream_(), dst);

        // Make sure users won't try to make changes to the temp file: their changes would be lost
        dst.setReadOnly();

        ev.setResult_(dst);
    }
}
