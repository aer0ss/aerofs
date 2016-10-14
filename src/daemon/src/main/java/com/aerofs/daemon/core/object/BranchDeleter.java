package com.aerofs.daemon.core.object;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

public class BranchDeleter
{
    private static final Logger l = Loggers.getLogger(BranchDeleter.class);
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final CentralVersionDatabase _cvdb;
    private final RemoteContentDatabase _rcdb;

    @Inject
    public BranchDeleter(DirectoryService ds, IPhysicalStorage ps,
                         CentralVersionDatabase cvdb, RemoteContentDatabase rcdb)
    {
        _ds = ds;
        _ps = ps;
        _cvdb = cvdb;
        _rcdb = rcdb;
    }

    public void deleteBanch_(SOID soid, KIndex kidx, Trans t)
            throws ExNotFound, IOException, SQLException
    {
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
    }
}
