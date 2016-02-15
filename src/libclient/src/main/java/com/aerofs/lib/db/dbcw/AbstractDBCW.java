package com.aerofs.lib.db.dbcw;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import com.aerofs.base.Loggers;
import com.aerofs.lib.SystemUtil;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExDBCorrupted;
import org.slf4j.Logger;

abstract class AbstractDBCW implements IDBCW
{
    public final static Logger l = Loggers.getLogger(AbstractDBCW.class);

    private final String _url;
    private final boolean _autoCommit;
    private final Properties _properties;
    private Connection _c;

    abstract void initImpl_(Statement stmt) throws SQLException;

    protected abstract boolean isConstraintViolation(SQLException e);

    protected abstract boolean isDBCorrupted(SQLException e);
    protected abstract String integrityCheck();

    protected AbstractDBCW(String url, boolean autoCommit)
    {
        this(url, autoCommit, new Properties());
    }

    protected AbstractDBCW(String url, boolean autoCommit, Properties properties)
    {
        _url = url;
        _autoCommit = autoCommit;
        _properties = properties;
    }

    @Override
    public void init_() throws SQLException
    {
        // do not call fini_() here since otherwise all the prepared statements associated with the
        // database connection would become invalid.

        if (_c != null) return;

        try {
            Connection c = DriverManager.getConnection(_url, _properties);

            try (Statement stmt = c.createStatement()) {
                initImpl_(stmt);
            }

            c.setAutoCommit(_autoCommit);
            _c = c;
        } catch (SQLException e) {
            throwIfDBCorrupted(e);
            throw e;
        }
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
            SystemUtil.fatal(e);
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

    @Override
    public final void throwIfDBCorrupted(SQLException e) throws ExDBCorrupted
    {
        if (isDBCorrupted(e)) {
            l.error("corrupt db: start integrity check");
            throw new ExDBCorrupted(integrityCheck());
        }
    }
}
