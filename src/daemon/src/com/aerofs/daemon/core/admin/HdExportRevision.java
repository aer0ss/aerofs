package com.aerofs.daemon.core.admin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.event.admin.EIExportRevision;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.FileUtil.FileName;
import com.google.inject.Inject;

public class HdExportRevision extends AbstractHdIMC<EIExportRevision> {
    private final IPhysicalStorage _ps;

    @Inject
    public HdExportRevision(IPhysicalStorage ps)
    {
        _ps = ps;
    }

    @Override
    protected void handleThrows_(EIExportRevision ev, Prio prio) throws Exception
    {
        // Create a temp file that has the same extension has the original file
        // This is important so that we can open the temp file using the appropriate program
        FileName file = FileName.fromBaseName(ev._path.last());
        File dst = FileUtil.createTempFile(file.base, file.extension, null, true);

        FileOutputStream os = new FileOutputStream(dst);
        try {
            InputStream is = _ps.getRevProvider().getRevInputStream_(ev._path, ev._index)._is;
            Util.copy(is, os);
            ev.setResult_(dst);
        } finally {
            os.close();
        }

        // Make sure users won't try to make changes to the temp file: their changes would be lost
        dst.setReadOnly();
    }
}
