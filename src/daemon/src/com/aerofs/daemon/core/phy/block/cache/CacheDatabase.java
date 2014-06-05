/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.cache;

import static com.aerofs.daemon.core.phy.block.cache.CacheSchema.*;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.block.BlockStorageDatabase;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
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
    static final Logger l = Loggers.getLogger(CacheDatabase.class);

    public CacheDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    @Inject
    public CacheDatabase(CoreDBCW coreDBCW)
    {
        this(coreDBCW.get());
    }

    private PreparedStatementWrapper _pswGetCacheAccess = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_BlockCache, C_BlockCache_Hash + "=?", C_BlockCache_Time));
    public long getCacheAccess(byte[] key) throws SQLException
    {
        PreparedStatementWrapper psw = _pswGetCacheAccess;
        try {
            PreparedStatement ps = psw.get(c());
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
            throw detectCorruption(e);
        }
    }

    private PreparedStatementWrapper _pswSetCacheAccess = new PreparedStatementWrapper(
            "replace into " + T_BlockCache +
                    "(" + C_BlockCache_Hash + "," + C_BlockCache_Time + ") VALUES(?,?)");
    public void setCacheAccess(byte[] key, long timestamp, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswSetCacheAccess;
        try {
            PreparedStatement ps = psw.get(c());
            ps.setBytes(1, key);
            ps.setLong(2, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    private PreparedStatementWrapper _pswDeleteCachedEntry = new PreparedStatementWrapper(
            DBUtil.deleteWhere(T_BlockCache, C_BlockCache_Hash + "=?"));
    public void deleteCachedEntry(byte[] key, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswDeleteCachedEntry;
        try {
            PreparedStatement ps = psw.get(c());
            ps.setBytes(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    private static class DBIterSortedAccessesRow extends AbstractDBIterator<ContentBlockHash>
    {
        public DBIterSortedAccessesRow(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public ContentBlockHash get_() throws SQLException {
            return new ContentBlockHash(_rs.getBytes(1));
        }
    }

    private PreparedStatementWrapper _pswGetSortedAccesses = new PreparedStatementWrapper(
            "select " + C_BlockCache_Hash + " from " + T_BlockCache
                    + " order by " + C_BlockCache_Time + " asc");
    public IDBIterator<ContentBlockHash> getSortedCacheAccessesIter() throws SQLException
    {
        PreparedStatementWrapper psw = _pswGetSortedAccesses;
        try {
            PreparedStatement ps = psw.get(c());
            return new DBIterSortedAccessesRow(ps.executeQuery());
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }
}
