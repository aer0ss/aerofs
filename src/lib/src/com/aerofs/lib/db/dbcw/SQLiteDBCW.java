package com.aerofs.lib.db.dbcw;

import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.aerofs.base.C;
import com.aerofs.lib.SystemUtil;

public class SQLiteDBCW extends AbstractDBCW implements IDBCW
{
    private final boolean _exclusiveLocking;
    private final boolean _wal;

    public SQLiteDBCW(String url, boolean autoCommit, boolean exclusiveLocking, boolean wal)
    {
        super(url, autoCommit);
        _exclusiveLocking = exclusiveLocking;
        _wal = wal;

        try {
            Class.forName("org.sqlite.JDBC").asSubclass(Driver.class);
        } catch (ClassNotFoundException e) {
            SystemUtil.fatal(e);
        }
    }

    @Override
    void initImpl_(Statement stmt) throws SQLException
    {
        // this is necessary to prevent the SQLite's writer lock from killing the transaction
        // the SQLiteJDBC library sets the timeout value through the Statement command
        // see sqlite3_busy_timeout() c code.
        stmt.setQueryTimeout((int)(C.YEAR / 1000));

        stmt.execute("pragma foreign_keys=false");

        if (_exclusiveLocking) {
            stmt.execute("pragma locking_mode=exclusive");
        }

        if (_wal) {
            boolean assertsEnabled = false;
            assert assertsEnabled = true;  // intentional side-effect
            if (!assertsEnabled) {
                stmt.execute("pragma journal_mode=wal");
            } else {
                ResultSet rs = stmt.executeQuery("pragma journal_mode=wal");
                try {
                    if (!rs.next()) throw new SQLException("no results returned");

                    String str = rs.getString(1);
                    // we allow both in-memory database and persistent database with WAL enabled
                    if (!str.equals("wal") && !str.equals("memory")) {
                        throw new SQLException("unexcepted mode: " + str);
                    }
                } finally {
                    rs.close();
                }
            }
        }
    }

    @Override
    protected boolean isConstraintViolation(SQLException e)
    {
        return e.getMessage().startsWith("[SQLITE_CONSTRAINT]");
    }

    @Override
    public String insertOrIgnore()
    {
        return "insert or ignore ";
    }

    @Override
    public String notNull()
    {
        return " not null ";
    }

    @Override
    public String charSet()
    {
        return " ";
    }

    @Override
    public String collateIgnoreCase()
    {
        return " nocase ";
    }

    @Override
    public String autoIncrement()
    {
        return " autoincrement ";
    }

    @Override
    public String chunkType()
    {
        return " text ";
    }

    @Override
    public String nameType()
    {
        return " text ";
    }

    @Override
    public String userIdType()
    {
        return " text ";
    }

    @Override
    public String fidType(int fidLen)
    {
        return " blob ";
    }

    @Override
    public String uniqueIdType()
    {
        return " blob ";
    }

    @Override
    public String longType()
    {
        return " integer ";
    }

    @Override
    public String boolType()
    {
        return " integer ";  // sqlite doesn't recognize "boolean"
    }

    @Override
    public int b2s(boolean b)
    {
        return b ? 1 : 0;
    }

    @Override
    public String roleType()
    {
        return " integer ";
    }

    @Override
    public boolean isMySQL()
    {
        return false;
    }

    @Override
    public boolean columnExists(String table, String column) throws SQLException {
        Statement s = getConnection().createStatement();
        try {
            ResultSet rs = s.executeQuery("pragma table_info(" + table + ")");
            try {
                while (rs.next()) {
                    if (rs.getString(2).equals(column)) return true;
                }
            } finally {
                rs.close();
            }
            return false;
        } finally {
            s.close();
        }
    }

    @Override
    public boolean tableExists(String tableName) throws SQLException {
        Statement s = getConnection().createStatement();
        try {
            boolean ok = false;
            ResultSet rs = s.executeQuery("select name from sqlite_master" +
                    " where type='table' and name='" + tableName + "'");
            try {
                ok = rs.next();
            } finally {
                rs.close();
            }
            return ok;
        } finally {
            s.close();
        }
    }
}
