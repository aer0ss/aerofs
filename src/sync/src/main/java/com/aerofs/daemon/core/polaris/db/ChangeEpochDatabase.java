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

    private final PreparedStatementWrapper _pswSetRemoteEpoch = new PreparedStatementWrapper(
            DBUtil.updateWhere(T_STORE, C_STORE_SIDX + "=?", C_STORE_LTS_REMOTE));
    public void setRemoteChangeEpoch_(SIndex sidx, long epoch, Trans t) throws SQLException
    {
        checkState(1 == update(_pswSetRemoteEpoch, epoch, sidx.getInt()));
    }
}
