package com.aerofs.daemon.lib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.aerofs.base.Loggers;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.PreparedStatementWrapper;
import org.slf4j.Logger;

import com.aerofs.lib.Util;
import com.aerofs.lib.db.dbcw.IDBCW;

public abstract class AbstractDatabase
{
    private static final Logger l = Loggers.getLogger(AbstractDatabase.class);
    protected final IDBCW _dbcw;

    protected AbstractDatabase(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    protected Connection c() throws SQLException
    {
        return _dbcw.getConnection();
    }

    protected boolean isValid(PreparedStatement ps)
    {
        if (ps == null) return false;
        try {
            return ps.getConnection() == c();
        } catch (SQLException e) {
            l.warn(Util.e(e));
            DBUtil.close(ps);
            return false;
        }
    }

    @FunctionalInterface
    protected static interface StatementRunner<T>
    {
        public T execute(PreparedStatement ps) throws SQLException;
    }

    protected <T> T exec(PreparedStatementWrapper psw, StatementRunner<T> r)
            throws SQLException
    {
        try {
            return r.execute(psw.get(c()));
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    protected SQLException detectCorruption(SQLException e)
    {
        _dbcw.throwIfDBCorrupted(e);
        return e;
    }
}
