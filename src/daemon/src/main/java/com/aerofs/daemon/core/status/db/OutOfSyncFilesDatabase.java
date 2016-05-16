/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.status.db;

import com.aerofs.daemon.core.status.SyncStatusVerifier;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import java.sql.SQLException;

import static com.aerofs.daemon.lib.db.CoreSchema.*;
import static com.google.common.base.Preconditions.checkState;

/**
 * Keep track of files that are out of sync for {@link SyncStatusVerifier}. The
 * same information is available in T_OA, but is more efficiently retrieved in a
 * separate table.
 */
public class OutOfSyncFilesDatabase extends AbstractDatabase
{
    @Inject
    public OutOfSyncFilesDatabase(IDBCW dbcw) {
        super(dbcw);
    }

    private final PreparedStatementWrapper _pswInsertFile = new PreparedStatementWrapper(
            DBUtil.insertOrReplaceInto(T_OUT_OF_SYNC_FILES, C_OUT_OF_SYNC_FILES_SIDX,
                    C_OUT_OF_SYNC_FILES_OID, C_OUT_OF_SYNC_FILES_TIMESTAMP));
    public void insert_(SIndex sidx, OID oid, Trans t) throws SQLException {
        checkState(
                1 == update(_pswInsertFile, sidx.getInt(), oid.getBytes(), System.currentTimeMillis()));
    }

    private final PreparedStatementWrapper _pswDeleteFile = new PreparedStatementWrapper(DBUtil
            .deleteWhereEquals(T_OUT_OF_SYNC_FILES, C_OUT_OF_SYNC_FILES_SIDX, C_OUT_OF_SYNC_FILES_OID));
    public boolean delete_(SIndex sidx, OID oid, Trans t) throws SQLException {
        return 1 == update(_pswDeleteFile, sidx.getInt(), oid.getBytes());
    }

    private final PreparedStatementWrapper _pswDeleteRow = new PreparedStatementWrapper(
            DBUtil.deleteWhereEquals(T_OUT_OF_SYNC_FILES, C_OUT_OF_SYNC_FILES_IDX));
    public boolean delete_(long idx, Trans t) throws SQLException {
        return 1 == update(_pswDeleteRow, idx);
    }

    private final PreparedStatementWrapper _pswPageFiles = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_OUT_OF_SYNC_FILES,
                    C_OUT_OF_SYNC_FILES_IDX + ">? order by " + C_OUT_OF_SYNC_FILES_IDX + " limit ?",
                    C_OUT_OF_SYNC_FILES_IDX, C_OUT_OF_SYNC_FILES_SIDX, C_OUT_OF_SYNC_FILES_OID,
                    C_OUT_OF_SYNC_FILES_TIMESTAMP));
    public IDBIterator<OutOfSyncFile> selectPage_(long startingAfter, int limit)
            throws SQLException {
        return new AbstractDBIterator<OutOfSyncFile>(query(_pswPageFiles, startingAfter, limit)) {
            @Override
            public OutOfSyncFile get_() throws SQLException {
                return new OutOfSyncFile(_rs.getLong(1), new SIndex(_rs.getInt(2)),
                        new OID(_rs.getBytes(3)), _rs.getLong(4));
            }
        };
    }

    public static class OutOfSyncFile
    {
        public final long idx;
        public final SIndex sidx;
        public final OID oid;
        public final long timestamp;

        public OutOfSyncFile(long idx, SIndex sidx, OID oid, long timestamp) {
            this.idx = idx;
            this.sidx = sidx;
            this.oid = oid;
            this.timestamp = timestamp;
        }
    }
}
