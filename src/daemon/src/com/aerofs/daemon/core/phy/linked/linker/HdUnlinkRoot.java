package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.event.fs.EIUnlinkRoot;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.PendingRootDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

public class HdUnlinkRoot extends AbstractHdIMC<EIUnlinkRoot>
{

    private final IStores _ss;
    private final TransManager _tm;
    private final IPhysicalStorage _ps;
    private final StoreDeleter _sd;
    private final IMapSID2SIndex _sid2sidx;
    private final PendingRootDatabase _prdb;
    private final LinkerRootMap _lrm;

    @Inject
    public HdUnlinkRoot(IStores ss, IPhysicalStorage ps, TransManager tm, StoreDeleter sd,
        IMapSID2SIndex sid2sidx, PendingRootDatabase prdb, LinkerRootMap lrm)
    {
        _ss = ss;
        _ps = ps;
        _tm = tm;
        _sd = sd;
        _sid2sidx = sid2sidx;
        _prdb = prdb;
        _lrm = lrm;
    }

    /**
     * This function unlinks an external folder. It unlinks it as a root and
     * adds it as pending root.
     */
    @Override
    protected void handleThrows_(EIUnlinkRoot ev, Prio prio) throws Exception
    {
        SIndex sidxRoot = _sid2sidx.getThrows_(ev._sid);

        l.info("Unlink external root sid: {}", ev._sid);
        Trans t = _tm.begin_();
        try {
            _ps.discardRevForTrans_(t);
            _prdb.addPendingRoot(ev._sid, _ss.getName_(sidxRoot), t);
            // MAP is used here because we don't want to physically delete the user's files. MAP
            // will cleanup NROs and conflict branches.
            _sd.deleteRootStore_(sidxRoot, PhysicalOp.MAP, t);
            _lrm.unlink_(ev._sid, t);
            t.commit_();
            l.info("Unlinked external root sid: {}", ev._sid);
        } finally {
            t.end_();
        }
    }

}
