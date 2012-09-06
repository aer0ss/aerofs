package com.aerofs.lib.db.dbcw;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import com.aerofs.lib.ex.ExAlreadyExist;
import org.apache.log4j.Logger;

import com.aerofs.lib.Util;

abstract class AbstractDBCW implements IDBCW
{
    public final static Logger l = Util.l(AbstractDBCW.class);

    private final String _url;
    private final boolean _autoCommit;
    private Connection _c;

    abstract void initImpl_(Statement stmt) throws SQLException;

    protected abstract boolean isConstraintViolation(SQLException e);

    protected AbstractDBCW(String url, boolean autoCommit)
    {
        _url = url;
        _autoCommit = autoCommit;
    }

    @Override
    public void init_() throws SQLException
    {
        // do not call fini_() here since otherwise all the prepared statements associated with the
        // database connection would become invalid.

        if (_c != null) return;

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
    public final void abort_()
    {
        try {
            _c.rollback();
        } catch (SQLException e) {
            Util.fatal(e);
        }
    }


    @Override
    public final void commit_() throws SQLException
    {
        _c.commit();
    }

    @Override
    public final Connection getConnection()
    {
        assert _c != null;
        return _c;
    }

    @Override
    public String bloomFilterType()
    {
        return " blob ";
    }

    @Override
    public final void throwOnConstraintViolation(SQLException e) throws ExAlreadyExist
    {
        // Do not use e as the cause of the exception by calling new ExAlreadyExist(e), since the
        // stack trace of the new exception provides sufficient information, and it is unnecessary
        // to expose underlying implementation of the exception that wraps the implementation.
        if (isConstraintViolation(e)) throw new ExAlreadyExist();
    }

}
