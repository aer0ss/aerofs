/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sv;


import com.aerofs.servletlib.db.JUnitSVDatabaseParams;
import com.aerofs.servletlib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servletlib.db.SQLThreadLocalTransaction;
import com.aerofs.servletlib.sv.SVDatabase;
import com.aerofs.testlib.AbstractTest;
import org.junit.After;
import org.junit.Before;
import org.mockito.Spy;

import java.io.IOException;
import java.sql.SQLException;

public abstract class AbstractSVReactorTest extends AbstractTest
{
    // Inject a real (spy) local test SV database
    private final JUnitSVDatabaseParams _dbParams = new JUnitSVDatabaseParams();
    @Spy protected final SQLThreadLocalTransaction _transaction =
            new SQLThreadLocalTransaction(_dbParams.getProvider());
    @Spy protected SVDatabase db = new SVDatabase(_transaction);

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
