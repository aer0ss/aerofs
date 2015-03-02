/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.phy.linked.db;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.daemon.core.phy.linked.db.LinkedStorageSchema.*;
import static com.google.common.base.Preconditions.checkState;

/**
 * Keep track of folders that have been placed in the physical staging area,
 * from which they can be deleted incrementally.
 *
 * To be able to maintain sync history, the logical path of the topmost folder
 * must be kept in the DB. If sync history need not be kept, the path stored
 * in the DB is empty (NB: not null) and merely indicates in which physical
 * root the staged folder resides.
 */
public class LinkedStagingAreaDatabase extends AbstractDatabase
{
    @Inject
    public LinkedStagingAreaDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    private final PreparedStatementWrapper _pswAdd = new PreparedStatementWrapper(
            DBUtil.insert(T_PSA, C_PSA_PATH));
    public long addEntry_(Path path, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswAdd.get(c());
            ps.setString(1, path.toStringFormal());
            ps.executeUpdate();
            return DBUtil.generatedId(ps);
        } catch (SQLException e) {
            _pswAdd.close();
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswRemove = new PreparedStatementWrapper(
            DBUtil.deleteWhereEquals(T_PSA, C_PSA_ID));
    public void removeEntry_(long id, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswRemove.get(c());
            ps.setLong(1, id);
            int n = ps.executeUpdate();
            checkState(n == 1);
        } catch (SQLException e) {
            _pswRemove.close();
            throw detectCorruption(e);
        }
    }

    public static class StagedFolder
    {
        public final long id;
        public final Path historyPath;

        StagedFolder(long id, String historyPath) throws ExFormatError
        {
            this.id = id;
            this.historyPath = Path.fromStringFormal(historyPath);
        }
    }

    private static class DBIterator extends AbstractDBIterator<StagedFolder>
    {
        public DBIterator(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public StagedFolder get_() throws SQLException
        {
            try {
                return new StagedFolder(_rs.getLong(1), _rs.getString(2));
            } catch (ExFormatError e) {
                throw new SQLException(e);
            }
        }
    }

    private final PreparedStatementWrapper _pswGet = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_PSA, C_PSA_ID + ">?", C_PSA_ID, C_PSA_PATH));
    public IDBIterator<StagedFolder> listEntries_(long lastId) throws SQLException
    {
        try {
            PreparedStatement ps = _pswGet.get(c());
            ps.setLong(1, lastId);
            ResultSet rs = ps.executeQuery();
            return new DBIterator(rs);
        } catch (SQLException e) {
            _pswGet.close();
            throw detectCorruption(e);
        }
    }
}
