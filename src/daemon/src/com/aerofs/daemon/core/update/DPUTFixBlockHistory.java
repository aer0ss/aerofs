/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.util.EnumSet;

import static com.aerofs.daemon.core.phy.block.BlockStorageSchema.*;

/**
 * For the longest time BlockStorage on TeamServer did not correctly separate history
 * by SID, instead happily merging everything in a single "history root dir"
 *
 * The old history is placed in a temporary spot as writing a full migration is not considered
 * a worthwhile investment of engineering time given the low number of Block Storage users and
 * the even lower likelihood of any of them noticing the missing history.
 *
 * The data will remain in the DB and can always be migrated later on if a user complains about
 * its apparent absence.
 */
public class DPUTFixBlockHistory implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTFixBlockHistory(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    @Override
    public void run() throws Exception
    {
        // this fix is only required for BlockStorage derivatives, i.e. LOCAL and S3 at this time
        if (!EnumSet.of(StorageType.LOCAL, StorageType.S3).contains(Cfg.storageType())) return;

        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> s.executeUpdate("update " + T_DirHist
                + " set " + C_DirHist_Parent + "=-3"
                + " where " + C_DirHist_Parent + "=-2"));
    }
}
