/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.db;

import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.daemon.lib.db.CoreSchema.*;
import static com.google.common.base.Preconditions.checkState;

/**
 *
 */
public class ChangeEpochDatabase extends AbstractDatabase
{
    @Inject
    public ChangeEpochDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    private final PreparedStatementWrapper _pswGetEpoch = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_STORE, C_STORE_SIDX + "=?", C_STORE_LTS_LOCAL));
    public @Nullable Long getChangeEpoch_(SIndex sidx) throws SQLException
    {
        return exec(_pswGetEpoch, ps -> {
            ps.setInt(1, sidx.getInt());
            try (ResultSet rs = ps.executeQuery()) {
                checkState(rs.next());
                long epoch = rs.getLong(1);
                return rs.wasNull() ? null : epoch;
            }
        });
    }

    private final PreparedStatementWrapper _pswSetLocalEpoch = new PreparedStatementWrapper(
            DBUtil.updateWhere(T_STORE, C_STORE_SIDX + "=?", C_STORE_LTS_LOCAL));
    public void setChangeEpoch_(SIndex sidx, long epoch, Trans t) throws SQLException
    {
        setChangeEpoch_(_pswSetLocalEpoch, sidx, epoch, t);
    }

    private final PreparedStatementWrapper _pswSetRemoteEpoch = new PreparedStatementWrapper(
            DBUtil.updateWhere(T_STORE, C_STORE_SIDX + "=?", C_STORE_LTS_REMOTE));
    public void setRemoteChangeEpoch_(SIndex sidx, long epoch, Trans t) throws SQLException
    {
        setChangeEpoch_(_pswSetRemoteEpoch, sidx, epoch, t);
    }

    private void setChangeEpoch_(PreparedStatementWrapper psw, SIndex sidx, long epoch ,Trans t)
            throws SQLException
    {
        exec(psw, ps -> {
            ps.setLong(1, epoch);
            ps.setInt(2, sidx.getInt());
            checkState(ps.executeUpdate() == 1);
            return null;
        });
    }
}
