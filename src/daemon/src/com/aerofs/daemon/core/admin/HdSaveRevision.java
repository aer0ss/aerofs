package com.aerofs.daemon.core.admin;

import java.io.FileOutputStream;
import java.io.InputStream;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.proto.GetRevision;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.IMapSIndex2Store;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.admin.EISaveRevision;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;

public class HdSaveRevision extends AbstractHdIMC<EISaveRevision>
{
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final GetRevision _gr;
    private final InjectableFile.Factory _factFile;
    private final TokenManager _tokenManager;

    @Inject
    public HdSaveRevision(GetRevision gr, IPhysicalStorage ps,
            DirectoryService ds, IMapSIndex2Store sidx2s,
            InjectableFile.Factory factFile, TokenManager tokenManager)
    {
        _gr = gr;
        _ps = ps;
        _ds = ds;
        _factFile = factFile;
        _tokenManager = tokenManager;
    }

    @Override
    protected void handleThrows_(EISaveRevision ev, Prio prio) throws Exception
    {
        SIndex sidx = _ds.resolveThrows_(ev._path).sidx();

        InjectableFile f = _factFile.create(ev._dest);
        InjectableFile fPart = _factFile.create(ev._dest + ".part");

        if (!fPart.getParentFile().exists()) fPart.getParentFile().mkdir();

        boolean ok = false;
        try {
            FileOutputStream os = new FileOutputStream(fPart.getImplementation());
            try {
                if (ev._did.equals(Cfg.did())) {
                    InputStream is = _ps.getRevProvider().getRevInputStream_(ev._path, ev._index)._is;
                    Util.copy(is, os);

                } else {

                    Token tk = _tokenManager.acquire_(Cat.UNLIMITED, "gr " + ev._path);
                    try {
                        _gr.rpc_(sidx, ev._path, ev._index, ev._did, os, tk);
                    } finally {
                        tk.reclaim_();
                    }
                }

            } finally {
                os.close();
            }

            fPart.moveInSameFileSystem(f);
            ok = true;

        } finally {
            // delete the part file, ignore error.
            if (!ok) fPart.delete();
        }
    }
}
