package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.object.BranchDeleter;
import com.aerofs.daemon.event.fs.EIDeleteBranch;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;

import com.google.inject.Inject;

public class HdDeleteBranch extends AbstractHdIMC<EIDeleteBranch>
{
    private final DirectoryService _ds;
    private final TransManager _tm;
    private final BranchDeleter _bd;

    @Inject
    public HdDeleteBranch(TransManager tm, DirectoryService ds, BranchDeleter bd)
    {
        _tm = tm;
        _ds = ds;
        _bd = bd;
    }

    @Override
    protected void handleThrows_(EIDeleteBranch ev) throws Exception {
        // merge the version of the branch to the master branch
        if (ev._kidx.equals(KIndex.MASTER)) {
            throw new ExBadArgs("can't delete master branch");
        }

        // we need write_object permission as branch deletion is equivalent
        // to updating content on other devices
        SOID soid = _ds.resolveFollowAnchorThrows_(ev._path);
        try (Trans t = _tm.begin_()) {
            _bd.deleteBanch_(soid, ev._kidx, t);
            t.commit_();
        }
    }
}
