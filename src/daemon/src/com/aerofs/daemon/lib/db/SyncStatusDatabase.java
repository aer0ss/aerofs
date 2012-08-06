package com.aerofs.daemon.lib.db;

import static com.aerofs.lib.db.CoreSchema.C_EPOCH_SYNC_PULL;
import static com.aerofs.lib.db.CoreSchema.C_EPOCH_SYNC_PUSH;
import static com.aerofs.lib.db.CoreSchema.C_SSBS_OID;
import static com.aerofs.lib.db.CoreSchema.C_SSBS_SIDX;
import static com.aerofs.lib.db.CoreSchema.T_EPOCH;
import static com.aerofs.lib.db.CoreSchema.T_SSBS;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

/**
 * Local sync status DB
 *
 * To reduce storage overhead, instead of creating brand new tables, columns have
 * been added to existing tables.
 */
public class SyncStatusDatabase extends AbstractDatabase implements
        ISyncStatusDatabase {

    @Inject
    public SyncStatusDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    private long getEpochInternal_(PreparedStatementWrapper psw, String columnName)
            throws SQLException {
        try {
            if (psw.get() == null) {
                psw.set(c().prepareStatement("select " + columnName  + " from " + T_EPOCH));
            }
            long localEpoch = 0;
            ResultSet rs = psw.get().executeQuery();
            try {
                Util.verify(rs.next()); // there should be one entry
                localEpoch = rs.getLong(1);
                Util.verify(!rs.next()); // ... and only one entry
            } finally {
                rs.close();
            }
            return localEpoch;
        } catch (SQLException e) {
            _dbcw.checkDeadConnection(e);
            DBUtil.close(psw.get());
            psw.set(null);
            throw e;
        }
    }

    private void setEpochInternal_(PreparedStatementWrapper psw, String columnNane, long value)
            throws SQLException {
        try {
            if (psw.get() == null) {
                psw.set(c().prepareStatement("update " + T_EPOCH + " set " + columnNane + "=?"));
            }
            psw.get().setLong(1, value);
            int affectedRows = psw.get().executeUpdate();
            assert affectedRows == 1 : ("sync status epoch not updated");
        } catch (SQLException e) {
            _dbcw.checkDeadConnection(e);
            DBUtil.close(psw.get());
            psw.set(null);
            throw e;
        }
    }

    private PreparedStatementWrapper _pswGetPullEpoch = new PreparedStatementWrapper();
    @Override
    public long getPullEpoch_() throws SQLException
    {
        return getEpochInternal_(_pswGetPullEpoch, C_EPOCH_SYNC_PULL);
    }

    private PreparedStatementWrapper _pswUpdatePullEpoch = new PreparedStatementWrapper();
    @Override
    public void setPullEpoch_(long newEpoch, Trans t) throws SQLException
    {
        setEpochInternal_(_pswUpdatePullEpoch, C_EPOCH_SYNC_PULL, newEpoch);
    }

    private PreparedStatementWrapper _pswGetPushEpoch = new PreparedStatementWrapper();
    @Override
    public long getPushEpoch_() throws SQLException
    {
        return getEpochInternal_(_pswGetPushEpoch, C_EPOCH_SYNC_PUSH);
    }

    private PreparedStatementWrapper _pswUpdatePushEpoch = new PreparedStatementWrapper();
    @Override
    public void setPushEpoch_(long newIndex, Trans t) throws SQLException
    {
        setEpochInternal_(_pswUpdatePushEpoch, C_EPOCH_SYNC_PUSH, newIndex);
    }

    private static class DBIterSOID extends AbstractDBIterator<SOID>
    {
        DBIterSOID(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public SOID get_() throws SQLException
        {
            return new SOID(new SIndex(_rs.getInt(1)), new OID(_rs.getBytes(2)));
        }
    }

    private PreparedStatement _psGBO;
    @Override
    public IDBIterator<SOID> getBootstrapSOIDs_() throws SQLException
    {
        try {
            if (_psGBO == null) {
                _psGBO = c().prepareStatement("select " + C_SSBS_SIDX + "," + C_SSBS_OID +
                                              " from " + T_SSBS);
            }
            ResultSet rs = _psGBO.executeQuery();
            return new DBIterSOID(rs);
        } catch (SQLException e) {
            _dbcw.checkDeadConnection(e);
            DBUtil.close(_psGBO);
            _psGBO = null;
            throw e;
        }
    }

    private PreparedStatement _psRBO;
    @Override
    public void removeBootsrapSOID_(SOID soid, Trans t) throws SQLException
    {
        try {
            if (_psRBO == null) {
                _psRBO = c().prepareStatement("delete from " + T_SSBS +
                                              " where " + C_SSBS_SIDX + "=? and " + C_SSBS_OID + "=?");
            }
            _psRBO.setInt(1, soid.sidx().getInt());
            _psRBO.setBytes(2, soid.oid().getBytes());
            _psRBO.executeUpdate();
        } catch (SQLException e) {
            _dbcw.checkDeadConnection(e);
            DBUtil.close(_psGBO);
            _psGBO = null;
            throw e;
        }
    }
}
