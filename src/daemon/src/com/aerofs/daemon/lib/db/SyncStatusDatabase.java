package com.aerofs.daemon.lib.db;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

/**
 * see {@link ISyncStatusDatabase}
 *
 * NOTE: Whenever possible, use {@link com.aerofs.daemon.core.syncstatus.LocalSyncStatus} instead of
 * this class.
 */
public class SyncStatusDatabase extends AbstractDatabase implements ISyncStatusDatabase
{
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

    @Override
    public void bootstrap_(Trans t) throws SQLException
    {
        // clear the current push queue to avoid redundancy
        c().createStatement().executeUpdate("delete from " + T_SSPQ);
        // fill the push queue with all non-expelled objects
        markAllAdmittedObjectsAsModified(c());
    }

    /**
     * Public for use in two different post-update tasks
     */
    public static void markAllAdmittedObjectsAsModified(Connection c) throws SQLException
    {
        // ignore expelled object (non-zero flags) and store roots
        PreparedStatement ps = c.prepareStatement(
                "insert into " + T_SSPQ + "(" + C_SSPQ_SIDX + "," + C_SSPQ_OID + ")" +
                " select " + C_OA_SIDX + "," + C_OA_OID +
                " from " + T_OA + " where " + C_OA_FLAGS + "=0 and " + C_OA_OID + "!=?");

        ps.setBytes(1, OID.ROOT.getBytes());
        ps.executeUpdate();
    }

    @Override
    public void deleteModifiedObjectsForStore_(SIndex sidx, Trans t) throws SQLException
    {
        StoreDatabase.deleteRowsInTableForStore_(T_SSPQ, C_SSPQ_SIDX, sidx, c(), t);
    }

    private static class DBIterModifiedObject extends AbstractDBIterator<ModifiedObject>
    {
        DBIterModifiedObject(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public ModifiedObject get_() throws SQLException
        {
            long idx = _rs.getLong(1);
            SIndex sidx = new SIndex(_rs.getInt(2));
            OID oid = new OID(_rs.getBytes(3));
            return new ModifiedObject(idx, new SOID(sidx, oid));
        }
    }

    private PreparedStatement _psGMO;
    @Override
    public IDBIterator<ModifiedObject> getModifiedObjects_(long from) throws SQLException
    {
        try {
            if (_psGMO == null) _psGMO = c().prepareStatement(
                    "select " + C_SSPQ_IDX + "," + C_SSPQ_SIDX + "," + C_SSPQ_OID +
                            " from " + T_SSPQ + " where " + C_SSPQ_IDX + ">?" +
                            " order by " + C_SSPQ_IDX + " asc");

            _psGMO.setLong(1, from);
            ResultSet rs = _psGMO.executeQuery();
            return new DBIterModifiedObject(rs);
        } catch (SQLException e) {
            DBUtil.close(_psGMO);
            _psGMO = null;
            throw e;
        }
    }

    private PreparedStatement _psAMO;
    @Override
    public void addToModifiedObjects_(SOID soid, Trans t) throws SQLException
    {
        try {
            if (_psAMO == null) {
                _psAMO = c().prepareStatement("insert into " + T_SSPQ +
                        " (" + C_SSPQ_SIDX + "," + C_SSPQ_OID + ") values (?,?)");
            }
            _psAMO.setInt(1, soid.sidx().getInt());
            _psAMO.setBytes(2, soid.oid().getBytes());
            int rows = _psAMO.executeUpdate();
            assert rows == 1 : soid;
        } catch (SQLException e) {
            DBUtil.close(_psAMO);
            _psAMO = null;
            throw e;
        }
    }

    private PreparedStatement _psRMO;
    @Override
    public void removeModifiedObjects_(long idx, Trans t) throws SQLException
    {
        try {
            if (_psRMO == null) {
                _psRMO = c().prepareStatement("delete from " + T_SSPQ +
                        " where " + C_SSPQ_IDX + "<=?");
            }
            _psRMO.setLong(1, idx);
            _psRMO.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psRMO);
            _psRMO = null;
            throw e;
        }
    }
}
