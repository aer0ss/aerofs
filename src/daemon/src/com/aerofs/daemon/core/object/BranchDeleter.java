package com.aerofs.daemon.core.object;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Version;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

public class BranchDeleter
{
    private static final Logger l = Loggers.getLogger(BranchDeleter.class);
    private final NativeVersionControl _nvc;
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final VersionUpdater _vu;
    private final CfgUsePolaris _usePolaris;
    private final CentralVersionDatabase _cvdb;
    private final RemoteContentDatabase _rcdb;

    @Inject
    public BranchDeleter(Injector inj, DirectoryService ds, IPhysicalStorage ps,
                         VersionUpdater vu, CfgUsePolaris usePolaris)
    {
        _ds = ds;
        _ps = ps;
        _vu = vu;
        _usePolaris = usePolaris;
        _nvc = usePolaris.get() ? null : inj.getInstance(NativeVersionControl.class);
        _cvdb = usePolaris.get() ? inj.getInstance(CentralVersionDatabase.class) : null;
        _rcdb = usePolaris.get() ? inj.getInstance(RemoteContentDatabase.class) : null;
    }

    public void deleteBanch_(SOID soid, KIndex kidx, Trans t)
            throws ExNotFound, IOException, SQLException
    {
        if (_usePolaris.get()) {
            OA oa = _ds.getOAThrows_(soid);
            CA ca = oa.caThrows(kidx);
            l.info("delete branch {} {} {}", kidx, ca.length(), ca.mtime());
            // TODO(phoenix): update base version of MASTER CA?
            Long v = _cvdb.getVersion_(soid.sidx(), soid.oid());
            if (v == null) {
                // dummy conflict branch: need to bump version to latest know remote version
                // to "win" conflict
                Long max = _rcdb.getMaxVersion_(soid.sidx(), soid.oid());
                _cvdb.setVersion_(soid.sidx(), soid.oid(), max, t);
                _rcdb.deleteUpToVersion_(soid.sidx(), soid.oid(), max, t);
            }
            _ds.deleteCA_(soid, kidx, t);
            _ps.newFile_(_ds.resolve_(soid), kidx).delete_(PhysicalOp.APPLY, t);
            return;
        }

        SOCKID kBranch = new SOCKID(soid, CID.CONTENT, kidx);
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
        _nvc.addLocalVersion_(kMaster, vB_M, t);
        // no need to call _cd.updateMaxTicks() here as atomicWrite below
        // calls it any way
        _vu.update_(kMaster, t);
        deleteBranch_(kBranch, vBranch, true, t);
    }

    public void deleteBranch_(SOCKID k, Version v, boolean deletePhyFile, Trans t)
            throws ExNotFound, SQLException, IOException
    {
        deleteBranch_(k, v, deletePhyFile, false, t);
    }

    public void deleteBranch_(SOCKID k, Version v, boolean deletePhyFile,
            boolean deleteVersionPermanently, Trans t)
        throws SQLException, IOException
    {
        l.debug("delete branch {}", k);

        // During aliasing the MASTER branch of the aliased object can be deleted.

        assert k.cid().equals(CID.CONTENT);
        assert !v.isZero_();

        // TODO(phoenix)
        if (deleteVersionPermanently) _nvc.deleteLocalVersionPermanently_(k, v, t);
        else _nvc.deleteLocalVersion_(k, v, t);

        _ds.deleteCA_(k.soid(), k.kidx(), t);

        if (deletePhyFile) {
            _ps.newFile_(_ds.resolve_(k.soid()), k.kidx()).delete_(PhysicalOp.APPLY, t);
        }
    }
}
