package com.aerofs.lib.db;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import java.sql.SQLException;

import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.db.dbcw.SQLiteDBCW;
import com.aerofs.lib.injectable.InjectableDriver;

/**
 * An in-memory SQLite DBCW decorator for use in Unit Testing.
 */
public class InMemorySQLiteDBCW extends SQLiteDBCW
{
    private final InjectableDriver _dr;
    private final CfgUsePolaris _usePolaris;
    private final CoreDBCW _core;
    private boolean _finiWasCalled;

    /**
     * Mock database parameters and construct a component IDBCW with the mocks,
     * @param dr the Driver object that CoreSchema depends on
     */
    public InMemorySQLiteDBCW(InjectableDriver dr, CfgUsePolaris usePolaris)
    {
        super("jdbc:sqlite::memory:", false, true, false);
        _dr = dr;
        _usePolaris = usePolaris;

        _core = mock(CoreDBCW.class);
        when(_core.get()).thenReturn(this);
    }

    /**
     * Locally mock the Driver for classes that do not need a Driver
     */
    public InMemorySQLiteDBCW()
    {
        this(mock(InjectableDriver.class), mock(CfgUsePolaris.class));
    }

    public CoreDBCW getCoreDBCW()
    {
        return _core;
    }

    @Override
    protected void finalize() throws Throwable
    {
        assertTrue(_finiWasCalled);
        super.finalize();
    }

    /**
     * initialize the component DBCW, and then create the core schema.
     */
    @Override
    public void init_() throws SQLException
    {
        super.init_();
        new CoreSchema(_dr, _usePolaris).create_(getConnection().createStatement(), _core.get());
    }

    @Override
    public void fini_() throws SQLException
    {
        super.fini_();
        _finiWasCalled = true;
    }
}
