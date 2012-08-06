package com.aerofs.daemon.lib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;

import com.aerofs.lib.Util;
import com.aerofs.lib.db.dbcw.IDBCW;

public abstract class AbstractDatabase
{
    private static final Logger l = Util.l(AbstractDatabase.class);
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
            if (ps.getConnection() != c()) return false;
            return true;
        } catch (SQLException e) {
            l.warn(Util.e(e));
            close(ps);
            return false;
        }
    }

    protected void handleSQLException(SQLException e)
    {
        l.warn(Util.e(e));
        _dbcw.checkDeadConnection(e);
    }

    protected void handleSQLException(SQLException e, Statement stmt)
    {
        close(stmt);
        handleSQLException(e);
    }

    protected void handleSQLException(SQLException e, PreparedStatementWrapper psw)
    {
        close(psw.get());
        psw.set(null);
        handleSQLException(e);
    }

    protected static void close(Statement stmt)
    {
        try {
            if (stmt != null) stmt.close();
        } catch (SQLException e) {
            l.warn("cannot close stmt: " + e);
        }
    }

    protected class PreparedStatementWrapper
    {
        private PreparedStatement _ps;

        public PreparedStatementWrapper()
        {
        }

        public PreparedStatement get()
        {
            return _ps;
        }

        public PreparedStatement set(PreparedStatement ps)
        {
            return _ps = ps;
        }
    }
}
