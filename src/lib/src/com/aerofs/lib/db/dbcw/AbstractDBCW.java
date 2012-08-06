package com.aerofs.lib.db.dbcw;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;

import com.aerofs.lib.Util;

abstract class AbstractDBCW implements IDBCW
{
    public final static Logger l = Util.l(AbstractDBCW.class);

    private final String _url;
    private final boolean _autoCommit;
    private Connection _c;

    abstract void initImpl_(Statement stmt) throws SQLException;

    protected AbstractDBCW(String url, boolean autoCommit)
    {
        _url = url;
        _autoCommit = autoCommit;
    }

    @Override
    public void init_() throws SQLException
    {
        // do not call fini_() here since otherwise all the prepared statements associated with the
        // database connection would become invalid. See {@link IDBCW#fini_()}

        if (_c != null) return;

//        try {
//            Class.forName("nz.jdbcwrapper.WrapperDriver");
//        } catch (ClassNotFoundException e) {
//            throw new SQLException(e);
//        }
//        params = "jdbc:wrapper:trace=4;url=" + _url;

        Connection c = DriverManager.getConnection(_url);

        Statement stmt = c.createStatement();
        try {
            initImpl_(stmt);
        } finally {
            stmt.close();
        }

        c.setAutoCommit(_autoCommit);
        _c = c;
    }

    @Override
    public void fini_() throws SQLException
    {
        if (_c == null) return;

        _c.close();
        _c = null;
    }

    /*
     * Note: (http://sqlite.org/lang_transaction.html) The ROLLBACK will fail
     * with an error code SQLITE_BUSY if there are any pending queries. Both
     * read-only and read/write queries will cause a ROLLBACK to fail. A
     * ROLLBACK must fail if there are pending read operations (unlike COMMIT
     * which can succeed) because bad things will happen if the in-memory image
     * of the database is changed out from under an active query.
     */
    @Override
    public void abort_()
    {
        try {
            _c.rollback();
        } catch (SQLException e) {
            if (!checkDeadConnection(e)) Util.fatal(e);
        }
    }


    @Override
    public void commit_() throws SQLException
    {
        _c.commit();
    }

    @Override
    public Connection getConnection()
    {
        assert _c != null;
        return _c;
    }

    @Override
    public String bloomFilterType()
    {
        return " blob ";
    }
}
