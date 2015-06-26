package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.object.BranchDeleter;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.event.fs.EIDeleteBranch;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Version;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;

import com.google.inject.Inject;

public class HdDeleteBranch extends AbstractHdIMC<EIDeleteBranch>
{
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final NativeVersionControl _nvc;
    private final TransManager _tm;
    private final VersionUpdater _vu;
    private final BranchDeleter _bd;
    private final ChangeEpochDatabase _cedb;

    @Inject
    public HdDeleteBranch(VersionUpdater vu, TransManager tm, NativeVersionControl nvc,
            ChangeEpochDatabase cedb, DirectoryService ds, IPhysicalStorage ps, BranchDeleter bd)
    {
        _vu = vu;
        _tm = tm;
        _nvc = nvc;
        _ds = ds;
        _ps = ps;
        _bd = bd;
        _cedb = cedb;
    }

    @Override
    protected void handleThrows_(EIDeleteBranch ev) throws Exception
    {
        // merge the version of the branch to the master branch
        if (ev._kidx.equals(KIndex.MASTER)) {
            throw new ExBadArgs("can't delete master branch");
        }

        // we need write_object permission as branch deletion is equivalent
        // to updating content on other devices
        SOID soid = _ds.resolveFollowAnchorThrows_(ev._path);

        if (_cedb.getChangeEpoch_(soid.sidx()) != null) {
            OA oa = _ds.getOAThrows_(soid);
            CA ca = oa.caThrows(ev._kidx);
            l.info("delete branch {} {} {}", ev._kidx, ca.length(), ca.mtime());
            try (Trans t = _tm.begin_()) {
                // TODO(phoenix): update base version of MASTER CA?
                _ds.deleteCA_(soid, ev._kidx, t);
                _ps.newFile_(_ds.resolve_(soid), ev._kidx).delete_(PhysicalOp.APPLY, t);
                t.commit_();
            }
            return;
        }

        SOCKID kBranch = new SOCKID(soid, CID.CONTENT, ev._kidx);
        Version vBranch = _nvc.getLocalVersion_(kBranch);
        if (vBranch.isZero_()) throw new ExNotFound(kBranch.toString());

        SOCKID kMaster = new SOCKID(soid, CID.CONTENT, KIndex.MASTER);
        Version vMaster = _nvc.getLocalVersion_(kMaster);

        Version vB_M = vBranch.sub_(vMaster);
        // aliasing may create branches whose version vector is dominated by MASTER
        // so we cannot simply assert...
        if (vB_M.isZero_() || !vMaster.isDominatedBy_(vBranch)) {
            l.warn("del branch {} {} {}", kBranch, vBranch, vMaster);
        }

        try (Trans t = _tm.begin_()) {
            _nvc.addLocalVersion_(kMaster, vB_M, t);
            // no need to call _cd.updateMaxTicks() here as atomicWrite below
            // calls it any way
            _vu.update_(kMaster, t);
            _bd.deleteBranch_(kBranch, vBranch, true, t);
            t.commit_();
        }
    }
}
