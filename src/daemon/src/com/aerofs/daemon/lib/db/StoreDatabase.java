package com.aerofs.daemon.lib.db;

import static com.aerofs.lib.db.CoreSchema.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

/**
 * TODO split this giant class into smaller ones with better defined responsibilities, similar to
 * what we have done for, say, IVersionDatabase, IAliasDatabase, etc.
 */
public class StoreDatabase extends AbstractDatabase implements IStoreDatabase
{
    @Inject
    public StoreDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    @Override
    public Collection<StoreRow> getAll_() throws SQLException
    {
        ArrayList<StoreRow> srs = Lists.newArrayList();

        // we don't prepare the statement as the method is called infrequently
        Statement stmt = c().createStatement();
        try {
            ResultSet rs = stmt.executeQuery("select " + C_STORE_SIDX + "," + C_STORE_PARENT +
                    " from " + T_STORE);
            try {
                while (rs.next()) {
                    StoreRow sr = new StoreRow();
                    sr._sidx = new SIndex(rs.getInt(1));
                    sr._sidxParent = new SIndex(rs.getInt(2));
                    srs.add(sr);
                }
                return srs;
            } finally {
                rs.close();
            }
        } finally {
            DBUtil.close(stmt);
        }
    }

    private PreparedStatement _psAdd;
    @Override
    public void add_(SIndex sidx, SIndex sidxParent, Trans t) throws SQLException
    {
        try {
            if (_psAdd == null) {
                _psAdd = c().prepareStatement("insert into " + T_STORE + "(" + C_STORE_SIDX + "," +
                        C_STORE_PARENT + ") values(?,?)");
            }

            _psAdd.setInt(1, sidx.getInt());
            _psAdd.setInt(2, sidxParent.getInt());
            Util.verify(_psAdd.executeUpdate() == 1);
        } catch (SQLException e) {
            DBUtil.close(_psAdd);
            _psAdd = null;
            throw e;
        }
    }

    private PreparedStatement _psSP;
    @Override
    public void setParent_(SIndex sidx, SIndex sidxParent, Trans t) throws SQLException
    {
        try {
            if (_psSP == null) {
                _psSP = c().prepareStatement("update " + T_STORE + " set " + C_STORE_PARENT +
                        "=? where " + C_STORE_SIDX + "=?");
            }

            _psSP.setInt(1, sidxParent.getInt());
            _psSP.setInt(2, sidx.getInt());
            Util.verify(_psSP.executeUpdate() == 1);
        } catch (SQLException e) {
            DBUtil.close(_psSP);
            _psSP = null;
            throw e;
        }
    }

    @Override
    public void delete_(SIndex sidx, Trans t) throws SQLException
    {
        int rowsChanged = deleteRowsInTableForStore_(T_STORE, C_STORE_SIDX, sidx, c(), t);
        assert rowsChanged == 1 : rowsChanged;
    }

    private PreparedStatement _psGetDeviceList;
    @Override
    public byte[] getDeviceMapping_(SIndex sidx) throws SQLException
    {
        try {
            if (_psGetDeviceList == null) {
                _psGetDeviceList = c().prepareStatement(
                        "select " + C_STORE_DIDS + " from " + T_STORE + " where " + C_STORE_SIDX + "=?");
            }
            _psGetDeviceList.setInt(1, sidx.getInt());
            ResultSet rs = _psGetDeviceList.executeQuery();

            try {
                Util.verify(rs.next());
                byte[] dids = rs.getBytes(1);
                Util.verify(!rs.next());
                return dids;
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            DBUtil.close(_psGetDeviceList);
            _psGetDeviceList = null;
            throw e;
        }
    }
    private PreparedStatement _psSetDeviceList;

    @Override
    public void setDeviceMapping_(SIndex sidx, byte raw[], Trans t) throws SQLException
    {
        try {
            if (_psSetDeviceList == null) {
                _psSetDeviceList = c().prepareStatement(
                        "update " + T_STORE + " set " + C_STORE_DIDS + "=?" +
                                " where " + C_STORE_SIDX + "=?");
            }
            _psSetDeviceList.setBytes(1, raw);
            _psSetDeviceList.setInt(2, sidx.getInt());

            int affectedRows = _psSetDeviceList.executeUpdate();
            assert affectedRows == 1 : ("Duplicate SIndex");
        } catch (SQLException e) {
            DBUtil.close(_psSetDeviceList);
            _psSetDeviceList = null;
            throw e;
        }
    }

    /**
     * During store deletion, many databases must delete all rows in one or more tables for a given
     * store index. This static method helps avoid code duplication. An alternative is that each
     * database would extend a base class that provides such a method.
     * @param tables2columns a map of (table, sidx column) name pairs for those databases that
     *                       must delete data from multiple tables
     * @return the number of rows deleted across all tables
     */
    public static int deleteRowsInTablesForStore_(Map<String, String> tables2columns, SIndex sidx,
            Connection c, Trans t)
            throws SQLException
    {
        Statement stmt = c.createStatement();
        try {
            int rowsChanged = 0;

            for (Entry<String, String> t2c : tables2columns.entrySet()) {
                rowsChanged += stmt.executeUpdate("delete from " + t2c.getKey() + " where "
                        + t2c.getValue() + "=" + sidx.getInt());
            }
            return rowsChanged;
        } finally {
            DBUtil.close(stmt);
        }
    }

    /**
     * See deleteRowsInTablesForStore_. This method is for those databases with only one table
     * with store-related data to delete
     */
    public static int deleteRowsInTableForStore_(String tableName, String sidxColumnName,
            SIndex sidx, Connection c, Trans t)
            throws SQLException
    {
        return deleteRowsInTablesForStore_(ImmutableMap.of(tableName, sidxColumnName), sidx, c, t);
    }

}
