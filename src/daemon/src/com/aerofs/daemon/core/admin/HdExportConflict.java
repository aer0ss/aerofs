/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.event.admin.EIExportConflict;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.FileUtil.FileName;
import com.aerofs.lib.id.SOID;

import javax.inject.Inject;
import java.io.File;

// TODO: refactor to share code with other HdExport* classes
public class HdExportConflict extends AbstractHdIMC<EIExportConflict>
{
    private final DirectoryService _ds;
    @Inject
    public HdExportConflict(DirectoryService ds)
    {
        _ds = ds;
    }

    @Override
    protected void handleThrows_(EIExportConflict ev, Prio prio) throws Exception
    {
        SOID soid = _ds.resolveThrows_(ev._path);
        OA oa = _ds.getOA_(soid);
        CA ca = oa.ca(ev._kidx);
        IPhysicalFile pf = ca.physicalFile();
        File src = new File(pf.getAbsPath_());

        // Create a temp file that has the same extension has the original file
        // This is important so that we can open the temp file using the appropriate program
        FileName file = FileName.fromBaseName(oa.name());
        File dst = FileUtil.createTempFile(file.base, file.extension, null, true);

        // copy conflict file to temporary file
        FileUtil.copy(src, dst, false, false);

        // Make sure users won't try to make changes to the temp file: their changes would be lost
        dst.setReadOnly();

        ev.setResult_(dst);
    }
}
