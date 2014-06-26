/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.phy.linked.db.LinkedStorageSchema;
import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.SQLException;
import java.sql.Statement;

public class DPUTAddPhysicalStagingArea implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    public DPUTAddPhysicalStagingArea(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        if (Cfg.storageType() != StorageType.LINKED) return;

        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, new IDatabaseOperation() {
            @Override
            public void run_(Statement s) throws SQLException
            {
                LinkedStorageSchema.createPhysicalStagingAreaTable_(s, _dbcw);
            }
        });
    }
}
