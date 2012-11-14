package com.aerofs.daemon.lib.db;

import static com.aerofs.lib.db.CoreSchema.C_ACL_ROLE;
import static com.aerofs.lib.db.CoreSchema.C_ACL_SIDX;
import static com.aerofs.lib.db.CoreSchema.C_ACL_SUBJECT;
import static com.aerofs.lib.db.CoreSchema.C_EPOCH_ACL;
import static com.aerofs.lib.db.CoreSchema.T_ACL;
import static com.aerofs.lib.db.CoreSchema.T_EPOCH;
import static com.google.common.collect.Maps.immutableEntry;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

public class ACLDatabase extends AbstractDatabase implements IACLDatabase
{
    @Inject
    public ACLDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    private static class DBIterSubjectRole extends AbstractDBIterator<Map.Entry<String, Role>>
    {
        DBIterSubjectRole(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public Map.Entry<String, Role> get_() throws SQLException
        {
            return immutableEntry(_rs.getString(1), Role.values()[_rs.getInt(2)]);
        }
    }

    private PreparedStatement _psGet;
    @Override
    public IDBIterator<Map.Entry<String, Role>> get_(SIndex sidx) throws SQLException
    {
        try {
            if (_psGet == null) {
                _psGet = c().prepareStatement(
                        "select " + C_ACL_SUBJECT + "," + C_ACL_ROLE +
                                " from " + T_ACL + " where " + C_ACL_SIDX + "=?");
            }
            _psGet.setInt(1, sidx.getInt());

            return new DBIterSubjectRole(_psGet.executeQuery());

        } catch (SQLException e) {
            DBUtil.close(_psGet);
            _psGet = null;
            throw e;
        }
    }

    private PreparedStatement _psSet;
    @Override
    public void set_(SIndex sidx, Map<String, Role> subject2role, Trans t) throws SQLException
    {
        try {
            if (_psSet == null) {
                _psSet = c().prepareStatement(
                        "replace into " + T_ACL + "(" + C_ACL_SIDX + "," + C_ACL_SUBJECT + "," +
                                C_ACL_ROLE + ") values (?,?,?)");
            }

            _psSet.setInt(1, sidx.getInt());
            for (Entry<String, Role> en : subject2role.entrySet()) {
                _psSet.setString(2, en.getKey());
                _psSet.setInt(3, en.getValue().ordinal());
                _psSet.addBatch();
            }
            _psSet.executeBatch();

        } catch (SQLException e) {
            DBUtil.close(_psSet);
            _psSet = null;
            throw e;
        }
    }

    private PreparedStatement _psDel;
    @Override
    public void delete_(SIndex sidx, Iterable<String> subjects, Trans t) throws SQLException
    {
        try {
            if (_psDel == null) {
                _psDel = c().prepareStatement(
                        "delete from " + T_ACL + " where " + C_ACL_SIDX + "=? and " +
                                C_ACL_SUBJECT + "=?");
            }

            _psDel.setInt(1, sidx.getInt());
            for (String subject : subjects) {
                _psDel.setString(2, subject);
                _psDel.addBatch();
            }
            _psDel.executeBatch();

        } catch (SQLException e) {
            DBUtil.close(_psDel);
            _psDel = null;
            throw e;
        }
    }

    private PreparedStatement _psClear;
    @Override
    public void clear_(Trans t) throws SQLException
    {
        try {
            if (_psClear == null) {
                _psClear = c().prepareStatement("delete from " + T_ACL);
            }
            _psClear.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psClear);
            _psClear = null;
            throw e;
        }
    }

    private PreparedStatement _psGetEpoch;
    @Override
    public long getEpoch_()
            throws SQLException
    {
        try {
            if (_psGetEpoch == null) {
                // always select the first and only row in the epoch table
                _psGetEpoch = c().prepareStatement("select " + C_EPOCH_ACL  + " from " + T_EPOCH);
            }

            long localEpoch = 0;
            ResultSet rs = _psGetEpoch.executeQuery();
            try {
                Util.verify(rs.next()); // there should be one entry
                localEpoch = rs.getLong(1);
                Util.verify(!rs.next()); // ... and only one entry
            } finally {
                rs.close();
            }
            return localEpoch;

        } catch (SQLException e) {
            DBUtil.close(_psGetEpoch);
            _psGetEpoch = null;
            throw e;
        }
    }

    private PreparedStatement _psUpdateEpoch;
    @Override
    public void setEpoch_(long newEpoch, Trans t)
            throws SQLException
    {
        try {
            if (_psUpdateEpoch == null) {
                _psUpdateEpoch = c().prepareStatement("update " + T_EPOCH + " set " +
                        C_EPOCH_ACL + "=?");
            }
            _psUpdateEpoch.setLong(1, newEpoch);

            int affectedRows = _psUpdateEpoch.executeUpdate();
            assert affectedRows == 1 : ("acl epoch not updated"); // only one row should be updated

        } catch (SQLException e) {
            DBUtil.close(_psUpdateEpoch);
            _psUpdateEpoch = null;
            throw e;
        }
    }
}
