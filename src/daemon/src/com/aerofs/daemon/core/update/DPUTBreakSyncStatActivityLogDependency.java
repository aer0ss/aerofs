/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.daemon.lib.db.SyncStatusDatabase;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * Due to corner cases (mostly involving aliasing) SyncStatus reliance on ActivityLog to detect
 * version vector changes introduces bugs (changes not being picked up -> vh not being sent to
 * server -> status incorrectly reported as out of sync)
 *
 * A new table is introduced to replace the role previously held by the activity log table (and as
 * a result the old bootstrap table becomes redundant and is removed).
 */
public class DPUTBreakSyncStatActivityLogDependency implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    DPUTBreakSyncStatActivityLogDependency(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, new IDatabaseOperation()
        {
            @Override
            public void run_(Statement s) throws SQLException
            {
                // drop obsolete table
                if (_dbcw.tableExists(DPUTUpdateSchemaForSyncStatus.T_SSBS)) {
                    s.executeUpdate("drop table " + DPUTUpdateSchemaForSyncStatus.T_SSBS);
                }

                if (!_dbcw.tableExists(T_SSPQ)) {
                    // create new table
                    CoreSchema.createSyncStatusPushQueueTable(s, _dbcw);

                    // re-send version hashes for all non-expelled objects
                    SyncStatusDatabase.markAllAdmittedObjectsAsModified(_dbcw.getConnection());
                    s.executeUpdate("update " + T_EPOCH + " set " + C_EPOCH_SYNC_PUSH + "=0");
                }
            }
        });
    }
}
