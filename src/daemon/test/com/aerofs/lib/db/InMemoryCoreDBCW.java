package com.aerofs.lib.db;

import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.testlib.InMemorySQLiteDBCW;

import java.sql.SQLException;

import static org.mockito.Mockito.mock;

/**
 * An in-memory SQLite DBCW decorator for use in Unit Testing.
 */
public class InMemoryCoreDBCW extends InMemorySQLiteDBCW
{
    private final InjectableDriver _dr;

    /**
     * Mock database parameters and construct a component IDBCW with the mocks,
     * @param dr the Driver object that CoreSchema depends on
     */
    public InMemoryCoreDBCW(InjectableDriver dr)
    {
        super();
        _dr = dr;
    }

    /**
     * Locally mock the Driver for classes that do not need a Driver
     */
    public InMemoryCoreDBCW()
    {
        this(mock(InjectableDriver.class));
    }

    /**
     * initialize the component DBCW, and then create the core schema.
     */
    @Override
    public void init_() throws SQLException
    {
        super.init_();
        new CoreSchema(_dr).create_(getConnection().createStatement(), this);
    }
}
