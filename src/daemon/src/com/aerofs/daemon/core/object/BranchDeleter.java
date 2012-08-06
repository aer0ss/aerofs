package com.aerofs.daemon.core.object;

import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCKID;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

public class BranchDeleter
{
    private static final Logger l = Util.l(BranchDeleter.class);
    private final NativeVersionControl _nvc;
    private final DirectoryService _ds;

    @Inject
    public BranchDeleter(DirectoryService ds, NativeVersionControl nvc)
    {
        _ds = ds;
        _nvc = nvc;
    }

    public void deleteBranch_(SOCKID k, Version v, boolean deletePhyFile, Trans t)
            throws ExNotFound, SQLException, IOException
    {
        deleteBranch_(k, v, deletePhyFile, false, t);
    }

    public void deleteBranch_(SOCKID k, Version v, boolean deletePhyFile,
            boolean deleteVersionPermanently, Trans t)
        throws SQLException, IOException, ExNotFound
    {
        l.info("delete branch " + k);

        // During aliasing the MASTER branch of the aliased object can be deleted.

        assert k.cid().equals(CID.CONTENT);
        assert !v.isZero_();

        final CA ca = _ds.getOANullable_(k.soid()).ca(k.kidx());
        // guaranteed by the caller
        assert ca != null;

        if (deleteVersionPermanently) _nvc.deleteLocalVersionPermanently_(k, v, t);
        else _nvc.deleteLocalVersion_(k, v, t);

        _ds.deleteCA_(k.soid(), k.kidx(), t);

        if (deletePhyFile) ca.physicalFile().delete_(PhysicalOp.APPLY, t);
    }
}
