package com.aerofs.daemon.core.admin;

import java.io.File;

import com.aerofs.daemon.core.phy.IPhysicalRevProvider.RevInputStream;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.tc.CoreLockReleasingExecutor;
import com.aerofs.daemon.event.admin.EIExportRevision;
import com.aerofs.lib.os.IOSUtil;
import com.google.inject.Inject;

public class HdExportRevision extends AbstractHdExport<EIExportRevision>
{
    private final IPhysicalStorage _ps;

    @Inject
    public HdExportRevision(IOSUtil os, CoreLockReleasingExecutor clre, IPhysicalStorage ps)
    {
        super(os, clre);
        _ps = ps;
    }

    @Override
    protected void handleThrows_(EIExportRevision ev) throws Exception
    {
        File dst = createTempFileWithSameExtension(ev._path.last());
        RevInputStream rev = _ps.getRevProvider().getRevInputStream_(ev._path, ev._index);
        exportOrDeleteDest_(rev._is, dst);

        ev.setResult_(dst);
        dst.setLastModified(rev._mtime);
    }
}
