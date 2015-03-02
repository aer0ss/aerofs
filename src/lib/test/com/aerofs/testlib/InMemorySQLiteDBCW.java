package com.aerofs.testlib;

import java.sql.SQLException;

import com.aerofs.lib.db.dbcw.SQLiteDBCW;

import static org.junit.Assert.assertTrue;

/**
 * An in-memory SQLite DBCW decorator for use in Unit Testing.
 */
public class InMemorySQLiteDBCW extends SQLiteDBCW
{
    private boolean _finiWasCalled;

    /**
     * Mock database parameters and construct a component IDBCW with the mocks,
     */
    public InMemorySQLiteDBCW()
    {
        super("jdbc:sqlite::memory:", false, true, false);
    }

    @Override
    protected void finalize() throws Throwable
    {
        assertTrue(_finiWasCalled);
        super.finalize();
    }

    /**
     * initialize the component DBCW
     */
    @Override
    public void init_() throws SQLException
    {
        super.init_();
    }

    @Override
    public void fini_() throws SQLException
    {
        super.fini_();
        _finiWasCalled = true;
    }
}
