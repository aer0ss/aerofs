/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.IDirectoryServiceListener;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.object.BranchDeleter;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.id.*;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

public class LegacyContentReceiver extends ContentReceiver implements IDirectoryServiceListener
{
    private static final Logger l = Loggers.getLogger(LegacyContentReceiver.class);

    private final DirectoryService _ds;
    private final NativeVersionControl _nvc;
    private final BranchDeleter _bd;
    private final CfgUsePolaris _usePolaris;

    @Inject
    public LegacyContentReceiver(DirectoryService ds, PrefixVersionControl pvc, NativeVersionControl nvc,
                                 IPhysicalStorage ps, DownloadState dlState, CfgUsePolaris usePolaris,
                                 IncomingStreams iss, BranchDeleter bd, TransManager tm,
                                 CoreScheduler sched, ProgressIndicators pi)
    {
        super(pvc, ps, dlState, iss, tm, sched, pi);
        _ds = ds;
        _nvc = nvc;
        _bd = bd;
        _usePolaris = usePolaris;
        _ds.addListener_(this);
    }

    // TODO(phoenix): SA/daemon extract
    @Override
    public void objectExpelled_(SOID expulsionRoot, Trans t) throws SQLException {
        for (OngoingTransfer dl : _ongoing) {
            OA oa = _ds.getOANullable_(dl.soid());
            if (oa == null || oa.isExpelled()) dl.abort();
        }
    }

    @Override
    protected void onMissingConflictBranch_(SOKID k)
            throws SQLException, IOException, ExNotFound
    {
        // A conflict branch does not exist, even though there is an entry
        // in our database. This is an inconsistency, we must remove the
        // entry in our database. AeroFS will redownload the conflict at a later
        // point.
        l.error("known conflict branch has no associated file: {}", k);

        try (Trans t = _tm.begin_()) {
            if (_usePolaris.get()) {
                // TODO(phoenix): version adjustment?
                //   rewind -> reset central version to base version of MASTER ca
                //   merge  -> update base version of MASTER CA
                // NB: currently, not doing anything is equivalent to a merge
                _ds.deleteCA_(k.soid(), k.kidx(), t);
            } else {
                SOCKID conflict = new SOCKID(k, CID.CONTENT);
                _bd.deleteBranch_(conflict, _nvc.getLocalVersion_(conflict), false, t);
            }
            t.commit_();
        }
    }
}
