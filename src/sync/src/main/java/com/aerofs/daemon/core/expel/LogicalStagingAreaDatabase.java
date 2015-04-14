/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.expel;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.ids.OID;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.daemon.lib.db.SyncSchema.*;
import static com.google.common.base.Preconditions.checkState;

public class LogicalStagingAreaDatabase extends AbstractDatabase
{
    @Inject
    public LogicalStagingAreaDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    private final PreparedStatementWrapper _pswGet = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_LSA, C_LSA_SIDX + "=? AND " + C_LSA_OID + "=?", C_LSA_HISTORY_PATH));
    /**
     * @return null if SOID not staged, empty if history should not be kept
     */
    public @Nullable Path historyPath_(SOID soid) throws SQLException
    {
        try {
            PreparedStatement ps = _pswGet.get(c());
            ps.setInt(1, soid.sidx().getInt());
            ps.setBytes(2, soid.oid().getBytes());
            try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Path.fromStringFormal(rs.getString(1)) : null;
            } catch (ExFormatError e) {
                throw new SQLException(e);
            }
        } catch (SQLException e) {
            _pswGet.close();
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswAdd = new PreparedStatementWrapper(
            DBUtil.insert(T_LSA, C_LSA_SIDX, C_LSA_OID, C_LSA_HISTORY_PATH));
    public void addEntry_(SOID soid, @Nonnull Path historyPath, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswAdd.get(c());
            ps.setInt(1, soid.sidx().getInt());
            ps.setBytes(2, soid.oid().getBytes());
            ps.setString(3, historyPath.toStringFormal());
            int n = ps.executeUpdate();
            checkState(n == 1);
        } catch (SQLException e) {
            _pswAdd.close();
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswRemove = new PreparedStatementWrapper(
            DBUtil.deleteWhereEquals(T_LSA, C_LSA_SIDX, C_LSA_OID));
    public void removeEntry_(SOID soid, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswRemove.get(c());
            ps.setInt(1, soid.sidx().getInt());
            ps.setBytes(2, soid.oid().getBytes());
            int n = ps.executeUpdate();
            checkState(n == 1);
        } catch (SQLException e) {
            _pswRemove.close();
            throw detectCorruption(e);
        }
    }

    public static class StagedFolder
    {
        public final SOID soid;
        public final Path historyPath;

        StagedFolder(SOID soid, Path historyPath)
        {
            this.soid = soid;
            this.historyPath = historyPath;
        }
    }

    private final PreparedStatementWrapper _pswList = new PreparedStatementWrapper(
            DBUtil.select(T_LSA, C_LSA_SIDX, C_LSA_OID, C_LSA_HISTORY_PATH));
    public IDBIterator<StagedFolder> listEntries_() throws SQLException
    {
        try {
            PreparedStatement ps = _pswList.get(c());
            ResultSet rs = ps.executeQuery();
            return new AbstractDBIterator<StagedFolder>(rs) {
                @Override
                public StagedFolder get_() throws SQLException
                {
                    try {
                        return new StagedFolder(
                                new SOID(new SIndex(_rs.getInt(1)), new OID(_rs.getBytes(2))),
                                Path.fromStringFormal(_rs.getString(3)));
                    } catch (ExFormatError e) {
                        throw new SQLException(e);
                    }
                }
            };
        } catch (SQLException e) {
            _pswList.close();
            throw detectCorruption(e);
        }
    }

    public boolean hasMoreEntries_(SIndex sidx) throws SQLException
    {
        try (IDBIterator<StagedFolder> it = listEntriesByStore_(sidx)) {
            return it.next_();
        }
    }

    private final PreparedStatementWrapper _pswListByStore = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_LSA, C_LSA_SIDX + "=?", C_LSA_OID, C_LSA_HISTORY_PATH));
    public IDBIterator<StagedFolder> listEntriesByStore_(final SIndex sidx) throws SQLException
    {
        try {
            PreparedStatement ps = _pswListByStore.get(c());
            ps.setInt(1, sidx.getInt());
            ResultSet rs = ps.executeQuery();
            return new AbstractDBIterator<StagedFolder>(rs) {
                @Override
                public StagedFolder get_() throws SQLException
                {
                    try {
                        return new StagedFolder(new SOID(sidx, new OID(_rs.getBytes(1))),
                                Path.fromStringFormal(_rs.getString(2)));
                    } catch (ExFormatError e) {
                        throw new SQLException(e);
                    }
                }
            };
        } catch (SQLException e) {
            _pswListByStore.close();
            throw detectCorruption(e);
        }
    }
}
