package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.fs.EIGetAttr;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdGetAttr extends AbstractHdIMC<EIGetAttr>
{
    private final DirectoryService _ds;

    @Inject
    public HdGetAttr(DirectoryService ds)
    {
        _ds = ds;
    }

    @Override
    public void handleThrows_(EIGetAttr ev, Prio prio) throws Exception
    {
        // Do not follow anchor
        SOID soid = _ds.resolveNullable_(ev._path);
        if (soid == null) {
            ev.setResult_(null);
        } else {
            OA oa = _ds.getOANullable_(soid);
            // oa may be null
            ev.setResult_(oa);
        }
    }
}
