package com.aerofs.daemon.lib.db;

import static com.aerofs.daemon.lib.db.SyncSchema.*;
import static com.aerofs.lib.db.DBUtil.*;
import static com.google.common.base.Preconditions.checkState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

/**
 * TODO split this giant class into smaller ones with better defined responsibilities, similar to
 * what we have done for, say, IVersionDatabase, IAliasDatabase, etc.
 */
public class StoreDatabase extends AbstractDatabase
        implements IStoreDatabase, ICollectorStateDatabase
{
    @Inject
    public StoreDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    private PreparedStatementWrapper _pswGA = new PreparedStatementWrapper(
            select(T_STORE, C_STORE_SIDX));
    @Override
    public Set<SIndex> getAll_() throws SQLException
    {
        try {
            PreparedStatement ps = _pswGA.get(c());
            try (ResultSet rs = ps.executeQuery()) {
                Set<SIndex> srs = Sets.newTreeSet();
                while (rs.next()) checkState(srs.add(new SIndex(rs.getInt(1))));
                return srs;
            }
        } catch (SQLException e) {
            _pswGA.close();
            throw detectCorruption(e);
        }
    }

    private PreparedStatementWrapper _pswGN = new PreparedStatementWrapper(
            selectWhere(T_STORE, C_STORE_SIDX + "=?", C_STORE_NAME));
    @Override
    public String getName_(SIndex sidx) throws SQLException
    {
        try {
            PreparedStatement ps = _pswGN.get(c());
            ps.setInt(1, sidx.getInt());
            try (ResultSet rs = ps.executeQuery()) {
                checkState(rs.next());
                String name = rs.getString(1);
                // the name may be null before DLTFetchStoreNames is executed.
                return name != null ? name : "";
            }
        } catch (SQLException e) {
            _pswGN.close();
            throw detectCorruption(e);
        }
    }

    private PreparedStatementWrapper _pswGCC = new PreparedStatementWrapper(
            selectWhere(T_STORE, C_STORE_SIDX + "=?", C_STORE_COLLECTING_CONTENT));
    @Override
    public boolean isCollectingContent_(SIndex sidx) throws SQLException
    {
        try {
            PreparedStatement ps = _pswGCC.get(c());
            ps.setInt(1, sidx.getInt());
            try (ResultSet rs = ps.executeQuery()) {
                checkState(rs.next());
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            _pswGCC.close();
            throw detectCorruption(e);
        }
    }

    private PreparedStatementWrapper _pswSCC = new PreparedStatementWrapper(
            updateWhere(T_STORE, C_STORE_SIDX + "=?", C_STORE_COLLECTING_CONTENT));
    @Override
    public void setCollectingContent_(SIndex sidx, boolean collectingContent, Trans t)
            throws SQLException
    {
        try {
            PreparedStatement ps = _pswSCC.get(c());
            ps.setBoolean(1, collectingContent);
            ps.setInt(2, sidx.getInt());
            checkState(ps.executeUpdate() == 1);
        } catch (SQLException e) {
            _pswSCC.close();
            throw detectCorruption(e);
        }
    }

    @Override
    public boolean hasAny_() throws SQLException
    {
        // we don't prepare the statement as the method is called infrequently
        Statement stmt = c().createStatement();
        try {
            try (ResultSet rs = stmt.executeQuery(select(T_STORE, "count(*)"))) {
                Util.verify(rs.next());
                int count = rs.getInt(1);
                assert !rs.next();
                return count != 0;
            }
        } finally {
            DBUtil.close(stmt);
        }
    }

    static boolean _assertsEnabled;
    static {
        //noinspection AssertWithSideEffects,ConstantConditions
        assert _assertsEnabled = true; // Intentional side effect!
    }

    private PreparedStatement _psAE;
    @Override
    public void assertExists_(SIndex sidx) throws SQLException
    {
        if (!_assertsEnabled) return;

        try {
            if (_psAE == null) _psAE = c().prepareStatement(
                    selectWhere(T_STORE, C_STORE_SIDX + "=?", "count(*)"));

            _psAE.setInt(1, sidx.getInt());
            try (ResultSet rs = _psAE.executeQuery()) {
                Util.verify(rs.next());
                assert rs.getInt(1) == 1 : sidx;
                assert !rs.next();
            }
        } catch (SQLException e) {
            DBUtil.close(_psAE);
            _psAE = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psAdd;
    @Override
    public void insert_(SIndex sidx, String name, Trans t) throws SQLException
    {
        try {
            if (_psAdd == null) {
                _psAdd = c().prepareStatement(insert(T_STORE, C_STORE_SIDX, C_STORE_NAME,
                        C_STORE_COLLECTING_CONTENT));
            }

            _psAdd.setInt(1, sidx.getInt());
            _psAdd.setString(2, name);
            // Collect content by default
            _psAdd.setBoolean(3, true);
            Util.verify(_psAdd.executeUpdate() == 1);
        } catch (SQLException e) {
            DBUtil.close(_psAdd);
            _psAdd = null;
            throw detectCorruption(e);
        }
        try (Statement s = c().createStatement()) {
            s.executeUpdate("update " + T_STORE
                            + " set " + C_STORE_LTS_LOCAL + "=-1"
                            + " where " +  C_STORE_SIDX + "=" + sidx.getInt());
        }
    }

    @Override
    public void delete_(SIndex sidx, Trans t) throws SQLException
    {
        int rowsChanged = deleteRowsInTableForStore_(T_STORE, C_STORE_SIDX, sidx, c(), t);
        assert rowsChanged == 1 : rowsChanged;
    }

    private PreparedStatement _psAP;
    @Override
    public void insertParent_(SIndex sidx, SIndex sidxParent, Trans t) throws SQLException
    {
        try {
            if (_psAP == null) {
                _psAP = c().prepareStatement(insert(T_SH, C_SH_SIDX, C_SH_PARENT_SIDX));
            }

            _psAP.setInt(1, sidx.getInt());
            _psAP.setInt(2, sidxParent.getInt());
            Util.verify(_psAP.executeUpdate() == 1);
        } catch (SQLException e) {
            DBUtil.close(_psAP);
            _psAP = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psDP;
    @Override
    public void deleteParent_(SIndex sidx, SIndex sidxParent, Trans t) throws SQLException
    {
        try {
            if (_psDP == null) {
                _psDP = c().prepareStatement(deleteWhereEquals(T_SH, C_SH_SIDX, C_SH_PARENT_SIDX));
            }

            _psDP.setInt(1, sidx.getInt());
            _psDP.setInt(2, sidxParent.getInt());
            Util.verify(_psDP.executeUpdate() == 1);
        } catch (SQLException e) {
            DBUtil.close(_psDP);
            _psDP = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatementWrapper _psrGC = new PreparedStatementWrapper(
            selectWhere(T_SH, C_SH_PARENT_SIDX + "=?", C_SH_SIDX));
    @Override
    public Set<SIndex> getChildren_(SIndex sidx) throws SQLException
    {
        return getChildrenOrParents_(_psrGC, sidx);
    }

    private PreparedStatementWrapper _psrGP = new PreparedStatementWrapper(
            selectWhere(T_SH, C_SH_SIDX + "=?", C_SH_PARENT_SIDX));
    @Override
    public Set<SIndex> getParents_(SIndex sidx) throws SQLException
    {
        return getChildrenOrParents_(_psrGP, sidx);
    }

    private Set<SIndex> getChildrenOrParents_(PreparedStatementWrapper psw, SIndex sidx)
            throws SQLException
    {
        Set<SIndex> children = Sets.newTreeSet();
        try {
            PreparedStatement ps = psw.get(c());
            ps.setInt(1, sidx.getInt());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) Util.verify(children.add(new SIndex(rs.getInt(1))));
                return children;
            }
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
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
                rowsChanged += stmt.executeUpdate(
                        deleteWhere(t2c.getKey(), t2c.getValue() + "=" + sidx.getInt()));
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
