package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.ComMonitor;
import com.aerofs.daemon.event.fs.EIEndWrite;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCKID;
import com.google.inject.Inject;

public class HdEndWrite extends AbstractHdIMC<EIEndWrite>
{

    private final TransManager _tm;
    private final ComMonitor _cm;

    @Inject
    public HdEndWrite(ComMonitor cm, TransManager tm)
    {
        _cm = cm;
        _tm = tm;
    }

    @Override
    protected void handleThrows_(EIEndWrite ev, Prio prio) throws Exception
    {
        SOCKID k = new SOCKID(ev._soid, CID.CONTENT);

        Trans t = _tm.begin_();
        try {
            //ev._pf.finalize_(t);
            _cm.endWrite_(k, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
