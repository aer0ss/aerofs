package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.event.admin.EISetExpelled;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdSetExpelled extends AbstractHdIMC<EISetExpelled>
{
    private final Expulsion _expulsion;
    private final DirectoryService _ds;
    private final TransManager _tm;

    @Inject
    public HdSetExpelled(Expulsion expulsion, DirectoryService ds, TransManager tm)
    {
        _expulsion = expulsion;
        _ds = ds;
        _tm = tm;
    }

    @Override
    protected void handleThrows_(EISetExpelled ev, Prio prio) throws Exception
    {
        SOID soid = _ds.resolveThrows_(ev._path);
        Trans t = _tm.begin_();
        try {
            _expulsion.setExpelled_(ev._expelled, soid, t);
            t.commit_();
        } catch (Exception e) {
            // make sure we get logging even if the rollback fails
            Util.l(this).warn((ev._expelled ? "expulsion" : "admission") + " failed " + soid, e);
            throw e;
        } finally {
            t.end_();
        }
    }

}
