/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.multiplicity.singleuser.ISharedFolderOp.SharedFolderOpType;
import com.aerofs.lib.db.dbcw.IDBCW;

import static com.aerofs.daemon.lib.db.CoreSchema.C_SPQ_NAME;
import static com.aerofs.daemon.lib.db.CoreSchema.C_SPQ_TYPE;
import static com.aerofs.daemon.lib.db.CoreSchema.T_SPQ;

/**
 * Updates Shared Folder Queue table: adds type and name columns - it allows to handle rename events
 * in addition to leave events
 */
public class DPUTUpdateSharedFoldersQueueTable implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;
    public DPUTUpdateSharedFoldersQueueTable(IDBCW dbcw)
    {
        this._dbcw = dbcw;
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            if (!_dbcw.columnExists(T_SPQ, C_SPQ_TYPE)) {
                s.executeUpdate("alter table " + T_SPQ +
                        " add column " + C_SPQ_TYPE + _dbcw.longType() + _dbcw.notNull()
                        + "default " + SharedFolderOpType.LEAVE.getValue());
            }
            if (!_dbcw.columnExists(T_SPQ, C_SPQ_NAME)) {
                s.executeUpdate("alter table " + T_SPQ +
                        " add column " + C_SPQ_NAME + _dbcw.nameType());
            }
        });
    }
}
