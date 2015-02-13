package com.aerofs.lib.db.dbcw;

import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;

import com.aerofs.lib.SystemUtil;
import com.aerofs.ids.UniqueID;

public class MySQLDBCW extends AbstractDBCW implements IDBCW
{
    private final Class<?> _clsConstraintViolationEx;

    public MySQLDBCW(String url, boolean autoCommit)
    {
        super(url, autoCommit);

        try {
            Class.forName("com.mysql.jdbc.Driver").asSubclass(Driver.class);
            String ns = "com.mysql.jdbc.exceptions.jdbc4.";
            _clsConstraintViolationEx = Class.forName(ns + "MySQLIntegrityConstraintViolationException");
        } catch (ClassNotFoundException e) {
            SystemUtil.fatal(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initImpl_(Statement stmt) throws SQLException
    {
    }

    @Override
    protected boolean isConstraintViolation(SQLException e)
    {
        return _clsConstraintViolationEx.isInstance(e);
    }

    @Override
    protected boolean isDBCorrupted(SQLException e)
    {
        return false;
    }

    @Override
    protected String integrityCheck()
    {
        return "integrityCheckNotSupported";
    }

    @Override
    public String insertOrIgnore()
    {
        return "insert ignore ";
    }

    @Override
    public String notNull()
    {
        return " is not null ";
    }

    @Override
    public String charSet()
    {
        return " charset=UTF8 ";
    }

    @Override
    public String autoIncrement()
    {
        return " auto_increment ";
    }

    @Override
    public String chunkType()
    {
        return " text ";
    }

    // we keep this number consistent, sometimes (in keys) we have to keep the
    // size low
    public static final String MYSQL_VAR_LEN = "255";

    @Override
    public String nameType()
    {
        return " varchar("+ MYSQL_VAR_LEN +")  ";
    }

    @Override
    public String userIdType()
    {
        return " varchar("+ MYSQL_VAR_LEN +")";
    }

    @Override
    public String fidType(int fidLen)
    {
        return " binary(" + fidLen + ") ";
    }

    @Override
    public String uniqueIdType()
    {
        return " binary(" + UniqueID.LENGTH + ") ";
    }

    @Override
    public String longType()
    {
        return " bigint ";
    }

    @Override
    public String boolType()
    {
        return " tinyint ";
    }

    @Override
    public int b2s(boolean b)
    {
        return b ? 1 : 0;
    }

    @Override
    public String roleType()
    {
        return " tinyint ";
    }

    @Override
    public boolean isMySQL()
    {
        return true;
    }

    @Override
    public boolean columnExists(String table, String column) throws SQLException
    {
        assert false : ("Method not implemented");
        return false;
    }

    @Override
    public boolean tableExists(String tableName) throws SQLException
    {
        assert false : ("Method not implemented");
        return false;
    }
}
