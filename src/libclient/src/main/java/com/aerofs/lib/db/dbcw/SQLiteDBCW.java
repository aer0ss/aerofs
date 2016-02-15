package com.aerofs.lib.db.dbcw;

import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import com.aerofs.base.C;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.os.OSUtil;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteConfig.LockingMode;
import org.sqlite.SQLiteJDBCLoader;
import org.sqlite.SQLiteJDBCLoader.INativeLibraryLoader;

public class SQLiteDBCW extends AbstractDBCW implements IDBCW
{
    static {
        SQLiteJDBCLoader.setCustomLoader(new INativeLibraryLoader() {
            @Override
            public boolean loadLibrary()
                    throws Exception
            {
                OSUtil.get().loadLibrary("sqlitejdbc");
                return true;
            }
        });
    }

    private static Properties makeProperties(boolean exclusiveLocking, boolean wal)
    {
        SQLiteConfig cfg = new SQLiteConfig();
        cfg.enforceForeignKeys(false);
        //cfg.setSynchronous(SynchronousMode.NORMAL);
        if (wal) cfg.setJournalMode(JournalMode.WAL);
        if (exclusiveLocking) cfg.setLockingMode(LockingMode.EXCLUSIVE);
        return cfg.toProperties();
    }

    public SQLiteDBCW(String url, boolean autoCommit, boolean exclusiveLocking, boolean wal)
    {
        super(url, autoCommit, makeProperties(exclusiveLocking, wal));

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
    }

    @Override
    protected boolean isConstraintViolation(SQLException e)
    {
        return e.getMessage().startsWith("[SQLITE_CONSTRAINT]");
    }

    @Override
    protected boolean isDBCorrupted(SQLException e)
    {
        return e.getMessage().startsWith("[SQLITE_CORRUPT]");
    }

    @Override
    protected String integrityCheck()
    {
        try {
            // make sure the CoreProgressWatcher doesn't kill the daemon even if the
            // integrity check takes a long time
            ProgressIndicators.get().startSyscall();
            try (Statement s = getConnection().createStatement()) {
                ResultSet rs = s.executeQuery("pragma integrity_check");
                StringBuilder bd = new StringBuilder();
                while (rs.next()) bd.append(rs.getString(1)).append('\n');
                return bd.toString();
            } finally {
                ProgressIndicators.get().endSyscall();

            }
        } catch (Throwable t) {
            return t.toString();
        }
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
        try (Statement s = getConnection().createStatement()) {
            try (ResultSet rs = s.executeQuery("pragma table_info(" + table + ")")) {
                while (rs.next()) {
                    if (rs.getString(2).equals(column)) return true;
                }
            }
            return false;
        }
    }

    @Override
    public boolean tableExists(String tableName) throws SQLException {
        try (Statement s = getConnection().createStatement()) {
            try (ResultSet rs = s.executeQuery("select name from sqlite_master" +
                    " where type='table' and name='" + tableName + "'")) {
                return rs.next();
            }
        }
    }
}
