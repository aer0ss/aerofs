package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.db.TamperingDetectionSchema;
import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.SQLException;
import java.sql.Statement;

public class DPUTAddTamperingDetectionTable implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    DPUTAddTamperingDetectionTable(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, new IDatabaseOperation() {
            @Override
            public void run_(Statement s) throws SQLException
            {
                new TamperingDetectionSchema().create_(s, _dbcw);
            }
        });
    }
}
