package com.aerofs.daemon.core.admin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import javax.inject.Inject;

import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.admin.EIExportFile;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.id.SOID;
import com.google.common.io.ByteStreams;

// TODO: refactor to share code with other HdExport* classes
public class HdExportFile extends AbstractHdIMC<EIExportFile>
{
    private final DirectoryService _ds;
    private final TokenManager _tokenManager;

    @Inject
    public HdExportFile(DirectoryService ds, TokenManager tokenManager)
    {
        _ds = ds;
        _tokenManager = tokenManager;
    }

    @Override
    protected void handleThrows_(EIExportFile ev, Prio prio) throws Exception
    {
        Token token = _tokenManager.acquire_(Cat.SERVER, "export file");

        SOID soid = _ds.resolveThrows_(ev._src);
        OA oa = _ds.getOAThrows_(soid);
        CA ca = oa.caMasterThrows();
        IPhysicalFile file = ca.physicalFile();

        File dst;

        InputStream in = file.newInputStream_();
        try {
            TCB tcb = (token == null) ? null : token.pseudoPause_("export file");
            try {
                dst = File.createTempFile("aerofs-export-", ".tmp");
                boolean ok = false;
                try {
                    OutputStream out = new FileOutputStream(dst);
                    try {
                        ByteStreams.copy(in, out);
                        out.flush();
                        ok = true;
                    } finally {
                        out.close();
                    }
                } finally {
                    if (!ok) dst.delete();
                }
            } finally {
                if (tcb != null) tcb.pseudoResumed_();
            }
        } finally {
            in.close();
        }

        ev.setResult_(dst);
    }
}
