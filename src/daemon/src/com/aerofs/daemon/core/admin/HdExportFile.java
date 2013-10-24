package com.aerofs.daemon.core.admin;

import java.io.File;

import javax.inject.Inject;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.tc.CoreLockReleasingExecutor;
import com.aerofs.daemon.event.admin.EIExportFile;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;

public class HdExportFile extends AbstractHdExport<EIExportFile>
{
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;

    @Inject
    public HdExportFile(CoreLockReleasingExecutor clre, DirectoryService ds, IPhysicalStorage ps)
    {
        super(clre);
        _ds = ds;
        _ps = ps;
    }

    @Override
    protected void handleThrows_(EIExportFile ev, Prio prio) throws Exception
    {
        SOID soid = _ds.resolveThrows_(ev._src);
        OA oa = _ds.getOAThrows_(soid);
        oa.caMasterThrows();
        IPhysicalFile pf = _ps.newFile_(_ds.resolve_(oa), KIndex.MASTER);

        File dst = createTempFileWithSameExtension(ev._src.last());

        exportOrDeleteDest_(pf.newInputStream_(), dst);

        ev.setResult_(dst);
    }
}
