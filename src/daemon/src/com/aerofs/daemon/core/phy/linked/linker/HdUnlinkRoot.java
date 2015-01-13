package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.event.fs.EIUnlinkRoot;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.UnlinkedRootDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

public class HdUnlinkRoot extends AbstractHdIMC<EIUnlinkRoot>
{
    private final StoreHierarchy _ss;
    private final TransManager _tm;
    private final IPhysicalStorage _ps;
    private final StoreDeleter _sd;
    private final IMapSID2SIndex _sid2sidx;
    private final UnlinkedRootDatabase _urdb;
    private final LinkerRootMap _lrm;

    @Inject
    public HdUnlinkRoot(StoreHierarchy ss, IPhysicalStorage ps, TransManager tm, StoreDeleter sd,
        IMapSID2SIndex sid2sidx, UnlinkedRootDatabase urdb, LinkerRootMap lrm)
    {
        _ss = ss;
        _ps = ps;
        _tm = tm;
        _sd = sd;
        _sid2sidx = sid2sidx;
        _urdb = urdb;
        _lrm = lrm;
    }

    /**
     * This function unlinks an external folder. It unlinks it as a root and
     * adds it as unlinked root.
     */
    @Override
    protected void handleThrows_(EIUnlinkRoot ev, Prio prio) throws Exception
    {
        SIndex sidxRoot = _sid2sidx.getThrows_(ev._sid);

        if (_lrm.get_(ev._sid) == null) throw new ExBadArgs();

        l.info("Unlink external root sid: {}", ev._sid);
        try (Trans t = _tm.begin_()) {
            _ps.discardRevForTrans_(t);
            _urdb.addUnlinkedRoot(ev._sid, _ss.getName_(sidxRoot), t);
            // MAP is used here because we don't want to physically delete the user's files. MAP
            // will cleanup NROs and conflict branches.
            // NB: Store deletion WILL call LinkerRootMap#unlink_
            _sd.deleteRootStore_(sidxRoot, PhysicalOp.MAP, t);
            t.commit_();
            l.info("Unlinked external root sid: {}", ev._sid);
        }
    }
}
