package com.aerofs.daemon.core.admin;

import java.io.File;

import javax.inject.Inject;

import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.event.admin.EIExportFile;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SOID;

public class HdExportFile extends AbstractHdExport<EIExportFile>
{
    private final DirectoryService _ds;

    @Inject
    public HdExportFile(TC tc, DirectoryService ds)
    {
        super(tc);
        _ds = ds;
    }

    @Override
    protected void handleThrows_(EIExportFile ev, Prio prio) throws Exception
    {
        SOID soid = _ds.resolveThrows_(ev._src);
        OA oa = _ds.getOAThrows_(soid);
        CA ca = oa.caMasterThrows();
        IPhysicalFile pf = ca.physicalFile();

        File dst = createTempFileWithSameExtension(ev._src.last());

        exportOrDeleteDest_(pf.newInputStream_(), dst);

        ev.setResult_(dst);
    }
}
