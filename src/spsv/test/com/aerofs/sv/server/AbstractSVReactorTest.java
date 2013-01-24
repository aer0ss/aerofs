/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sv.server;

import com.aerofs.servlets.lib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.testlib.AbstractTest;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.sql.SQLException;

public abstract class AbstractSVReactorTest extends AbstractTest
{
    private final SVDatabaseParams _dbParams = new SVDatabaseParams();
    protected final SQLThreadLocalTransaction _transaction =
            new SQLThreadLocalTransaction(_dbParams.getProvider());
    protected SVDatabase db = new SVDatabase(_transaction);

    @Before
    public void setupAbstractSVServiceTest()
            throws SQLException, ClassNotFoundException, IOException, InterruptedException
    {
        // Database setup.
        LocalTestDatabaseConfigurator.initializeLocalDatabase(_dbParams);
    }

    @After
    public void tearDownAbstractSVServiceTest()
            throws SQLException
    {
        _transaction.cleanUp();
    }
}
