/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.cache;

import static com.aerofs.daemon.core.phy.block.cache.CacheSchema.*;

import com.aerofs.daemon.core.phy.block.BlockStorageDatabase;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Keeps track of block access time for LRU eviction
 */
public class CacheDatabase extends AbstractDatabase
{
    static final Logger l = Util.l(CacheDatabase.class);

    public CacheDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    @Inject
    public CacheDatabase(CoreDBCW coreDBCW)
    {
        this(coreDBCW.get());
    }

    private PreparedStatementWrapper _pswGetCacheAccess = new PreparedStatementWrapper();
    public long getCacheAccess(byte[] key) throws SQLException
    {
        PreparedStatementWrapper psw = _pswGetCacheAccess;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "select " + C_BlockCache_Time +
                                " from " + T_BlockCache + " where " + C_BlockCache_Hash + "=?"));
            }
            ps.setBytes(1, key);
            ResultSet rs = ps.executeQuery();
            try {
                if (!rs.next()) return BlockStorageDatabase.DELETED_FILE_DATE;
                return rs.getLong(1);
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    private PreparedStatementWrapper _pswSetCacheAccess = new PreparedStatementWrapper();
    public void setCacheAccess(byte[] key, long timestamp, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswSetCacheAccess;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "replace into " + T_BlockCache +
                                "(" + C_BlockCache_Hash + "," + C_BlockCache_Time + ") VALUES(?,?)"));
            }
            ps.setBytes(1, key);
            ps.setLong(2, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }


    private PreparedStatementWrapper _pswDeleteCachedEntry = new PreparedStatementWrapper();
    public void deleteCachedEntry(byte[] key, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswDeleteCachedEntry;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "delete from " + T_BlockCache + " where " + C_BlockCache_Hash + "=?"));
            }
            ps.setBytes(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    private static class DBIterSortedAccessesRow extends AbstractDBIterator<ContentHash>
    {
        public DBIterSortedAccessesRow(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public ContentHash get_() throws SQLException {
            return new ContentHash(_rs.getBytes(1));
        }
    }

    private PreparedStatementWrapper _pswGetSortedAccesses = new PreparedStatementWrapper();
    public IDBIterator<ContentHash> getSortedCacheAccessesIter() throws SQLException
    {
        PreparedStatementWrapper psw = _pswGetSortedAccesses;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement("select " + C_BlockCache_Hash +
                        " from " + T_BlockCache + " order by " + C_BlockCache_Time + " asc"));
            }
            return new DBIterSortedAccessesRow(ps.executeQuery());
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }
}
