package com.aerofs.daemon.core.object;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Version;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCKID;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

public class BranchDeleter
{
    private static final Logger l = Loggers.getLogger(BranchDeleter.class);
    private final NativeVersionControl _nvc;
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;

    @Inject
    public BranchDeleter(DirectoryService ds, IPhysicalStorage ps, NativeVersionControl nvc)
    {
        _ds = ds;
        _ps = ps;
        _nvc = nvc;
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
