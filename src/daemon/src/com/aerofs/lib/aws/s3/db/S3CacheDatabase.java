package com.aerofs.lib.aws.s3.db;

import static com.aerofs.lib.db.S3Schema.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.aerofs.lib.db.PreparedStatementWrapper;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.dbcw.IDBCW;

public class S3CacheDatabase extends AbstractDatabase
{
    static final Logger l = Util.l(S3CacheDatabase.class);

    public S3CacheDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    @Inject
    public S3CacheDatabase(CoreDBCW coreDBCW)
    {
        this(coreDBCW.get());
    }

    private PreparedStatementWrapper _pswGetCacheAccess = new PreparedStatementWrapper();
    public long getCacheAccess(ContentHash hash) throws SQLException
    {
        PreparedStatementWrapper psw = _pswGetCacheAccess;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "select " + C_ChunkCache_Time +
                        " from " + T_ChunkCache + " where " + C_ChunkCache_Hash + "=?"));
            }
            ps.setBytes(1, hash.getBytes());
            ResultSet rs = ps.executeQuery();
            try {
                if (!rs.next()) return S3Database.DELETED_FILE_DATE;
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
    public void setCacheAccess(ContentHash hash, long timestamp, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswSetCacheAccess;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "replace into " + T_ChunkCache +
                        "(" + C_ChunkCache_Hash + "," + C_ChunkCache_Time + ") VALUES(?,?)"));
            }
            ps.setBytes(1, hash.getBytes());
            ps.setLong(2, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }


    private PreparedStatementWrapper _pswDeleteCachedEntry = new PreparedStatementWrapper();
    public void deleteCachedEntry(ContentHash hash, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswDeleteCachedEntry;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "delete from " + T_ChunkCache + " where " + C_ChunkCache_Hash + "=?"));
            }
            ps.setBytes(1, hash.getBytes());
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }


    private static class DBIterSortedAccessesRow extends AbstractDBIterator<ContentHash>
    {
        public DBIterSortedAccessesRow(ResultSet rs) {
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
                ps = psw.set(c().prepareStatement(
                        "select " + C_ChunkCache_Hash +
                        " from " + T_ChunkCache +
                        " order by " + C_ChunkCache_Time + " asc"));
            }
            return new DBIterSortedAccessesRow(ps.executeQuery());
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }
}
