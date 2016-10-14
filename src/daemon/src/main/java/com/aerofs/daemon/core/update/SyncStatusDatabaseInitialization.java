package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgSyncStatusEnabled;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;

import java.sql.SQLException;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * populates sync status tables initially, creating them first if necessary.
 *
 * TODO move to DPUT when {@link CfgSyncStatusEnabled} flag is removed
 */
public class SyncStatusDatabaseInitialization
{
    @Inject private IDBCW _dbcw;
    @Inject private CfgSyncStatusEnabled _cfgSyncStatusEnabled;
    @Inject private CfgLocalUser _cfgLocalUser;

    public void init_() throws SQLException {
        if (!_cfgSyncStatusEnabled.get()) return;

        populateOutOfSyncFilesDatabase();
    }

    private void populateOutOfSyncFilesDatabase() throws SQLException {
        if (_cfgLocalUser.get().isTeamServerID()) return;

        if (!_dbcw.tableExists(T_OUT_OF_SYNC_FILES)) {
            DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
                createOutOfSyncFilesTable(s, _dbcw);
                s.execute("insert or ignore into " + T_OUT_OF_SYNC_FILES + "(" + C_OUT_OF_SYNC_FILES_SIDX
                        + "," + C_OUT_OF_SYNC_FILES_OID + ", " + C_OUT_OF_SYNC_FILES_TIMESTAMP
                        + ") select " + C_OA_SIDX + "," + C_OA_OID + ", 0 from " + T_OA + " where "
                        + C_OA_TYPE + "=" + OA.Type.FILE.ordinal());
            });
        }
    }
}
