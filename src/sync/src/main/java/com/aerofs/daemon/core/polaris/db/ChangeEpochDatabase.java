/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.db;

import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.daemon.lib.db.SyncSchema.*;
import static com.google.common.base.Preconditions.checkState;

/**
 *
 */
public class ChangeEpochDatabase extends AbstractDatabase
{
    @Inject
    public ChangeEpochDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    private final PreparedStatementWrapper _pswGetEpoch = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_STORE, C_STORE_SIDX + "=?", C_STORE_LTS_LOCAL));
    public @Nullable Long getChangeEpoch_(SIndex sidx) throws SQLException
    {
        if (!_dbcw.columnExists(T_STORE, C_STORE_LTS_LOCAL)) return null;
        try (ResultSet rs = query(_pswGetEpoch, sidx.getInt())) {
            checkState(rs.next());
            long epoch = rs.getLong(1);
            return rs.wasNull() ? null : epoch;
        }
    }

    private final PreparedStatementWrapper _pswSetLocalEpoch = new PreparedStatementWrapper(
            DBUtil.updateWhere(T_STORE, C_STORE_SIDX + "=?", C_STORE_LTS_LOCAL));
    public void setChangeEpoch_(SIndex sidx, long epoch, Trans t) throws SQLException
    {
        checkState(1 == update(_pswSetLocalEpoch, epoch, sidx.getInt()));
    }

    /**
     * The "content change" epoch is a polaris logical timestamp used to tag Bloom Filter updates
     *
     * The collector will discard bloom filters as soon as it goes through a full iteration without
     * any transient errors. This means that bloom filters MUST not be accepted before the changes
     * they refer to have been fetched from polaris and added to the rmeote content / content fetch
     * queue.
     *
     * This epoch reflects the maximum logical timestamp for UPDATE_CONTENT transforms in the store.
     * It is updated when receiving an UPDATE_CONTENT from polaris and when a local content change
     * is successfully submitted.
     *
     * It is conceptually simple however it becomes tricky to maintain when migration is involved
     * since local changes are preserved but there is no way to infer what values will be assigned
     * to the corresponding transforms by polaris.
     *
     * See in particular the comment in ApplyChangeImpl#applyContentChange_ wrt "obsolete" changes
     */
    private final PreparedStatementWrapper _pswGetContentEpoch = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_STORE, C_STORE_SIDX + "=?", C_STORE_LTS_CONTENT));
    public @Nullable Long getContentChangeEpoch_(SIndex sidx) throws SQLException
    {
        if (!_dbcw.columnExists(T_STORE, C_STORE_LTS_CONTENT)) return null;
        try (ResultSet rs = query(_pswGetContentEpoch, sidx.getInt())) {
            checkState(rs.next());
            long epoch = rs.getLong(1);
            return rs.wasNull() ? null : epoch;
        }
    }

    private final PreparedStatementWrapper _pswSetContentEpoch = new PreparedStatementWrapper(
            DBUtil.updateWhere(T_STORE, C_STORE_SIDX + "=?", C_STORE_LTS_CONTENT));
    public void setContentChangeEpoch_(SIndex sidx, long epoch, Trans t) throws SQLException
    {
        checkState(1 == update(_pswSetContentEpoch, epoch, sidx.getInt()));
    }
}
