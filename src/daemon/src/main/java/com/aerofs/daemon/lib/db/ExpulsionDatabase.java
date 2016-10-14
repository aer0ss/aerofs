package com.aerofs.daemon.lib.db;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.ids.OID;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.daemon.lib.db.CoreSchema.C_EX_SIDX;
import static com.aerofs.daemon.lib.db.CoreSchema.C_EX_OID;
import static com.aerofs.daemon.lib.db.CoreSchema.T_EX;

public class ExpulsionDatabase extends AbstractDatabase implements IExpulsionDatabase
{
    @Inject
    public ExpulsionDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    private PreparedStatement _psAdd;
    @Override
    public void insertExpelledObject_(SOID soid, Trans t) throws SQLException
    {
        try {
            if (_psAdd == null) {
                _psAdd = c() .prepareStatement("insert into " + T_EX + " ( " +
                        C_EX_SIDX + "," + C_EX_OID + ") values (?,?)");
            }

            _psAdd.setInt(1, soid.sidx().getInt());
            _psAdd.setBytes(2, soid.oid().getBytes());

            _psAdd.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psAdd);
            _psAdd = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psDel;
    @Override
    public void deleteExpelledObject_(SOID soid, Trans t) throws SQLException
    {
        try {
            if (_psDel == null) {
                _psDel = c() .prepareStatement("delete from " + T_EX + " where " +
                        C_EX_SIDX + "=? and " + C_EX_OID + "=?");
            }

            _psDel.setInt(1, soid.sidx().getInt());
            _psDel.setBytes(2, soid.oid().getBytes());
            Util.verify(_psDel.executeUpdate() == 1);
        } catch (SQLException e) {
            DBUtil.close(_psDel);
            _psDel = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psDelStore;
    @Override
    public void deleteStore_(SIndex sidx, Trans t) throws SQLException
    {
        try {
            if (_psDelStore == null) {
                _psDelStore = c() .prepareStatement("delete from " + T_EX + " where " +
                        C_EX_SIDX + "=?");
            }

            _psDelStore.setInt(1, sidx.getInt());
            _psDelStore.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psDelStore);
            _psDelStore = null;
            throw detectCorruption(e);
        }
    }

    private static class DBIterExpelled extends AbstractDBIterator<SOID>
    {
        DBIterExpelled(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public SOID get_() throws SQLException
        {
            SIndex sidx = new SIndex(_rs.getInt(1));
            OID oid = new OID(_rs.getBytes(2));
            return new SOID(sidx, oid);
        }
    }

    private PreparedStatement _psGet;
    @Override
    public IDBIterator<SOID> getExpelledObjects_() throws SQLException
    {
        try {
            if (_psGet == null) {
                _psGet = c() .prepareStatement("select " + C_EX_SIDX + "," + C_EX_OID +
                        " from " + T_EX);
            }

            return new DBIterExpelled(_psGet.executeQuery());

        } catch (SQLException e) {
            DBUtil.close(_psGet);
            _psGet = null;
            throw detectCorruption(e);
        }
    }

}
