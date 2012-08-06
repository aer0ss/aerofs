package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.ComMonitor;
import com.aerofs.daemon.event.fs.EIPreWrite;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCKID;
import com.google.inject.Inject;

public class HdPreWrite extends AbstractHdIMC<EIPreWrite>
{
    private final TransManager _tm;
    private final ComMonitor _cm;

    @Inject
    public HdPreWrite(ComMonitor cm, TransManager tm)
    {
        _cm = cm;
        _tm = tm;
    }

    @Override
    protected void handleThrows_(EIPreWrite ev, Prio prio) throws Exception
    {
        Trans t = _tm.begin_();
        try {
            _cm.handlePreWriteEvent_(new SOCKID(ev._soid, CID.CONTENT),
                    // Updates to objects via HdPreWrite event handler
                    // are for non-alias updates.
                    false,
                    t);
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
