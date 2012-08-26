/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sv;


import com.aerofs.servletlib.db.JUnitDatabaseConnectionFactory;
import com.aerofs.servletlib.db.JUnitSVDatabaseParams;
import com.aerofs.servletlib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servletlib.sv.SVDatabase;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.mockito.Spy;

import java.io.IOException;
import java.sql.SQLException;

public abstract class AbstractSVReactorTest extends AbstractTest
{
    // Inject a real (spy) local test SP database into the SPService of AbstractSPServiceTest.
    private final JUnitSVDatabaseParams _dbParams = new JUnitSVDatabaseParams();
    @Spy protected SVDatabase db = new SVDatabase(new JUnitDatabaseConnectionFactory(_dbParams));

    @Before
    public void setupAbstractSVServiceTest()
            throws SQLException, ClassNotFoundException, IOException, InterruptedException
    {
        // Database setup.
        new LocalTestDatabaseConfigurator(_dbParams).configure_();
        db.init_();
    }
}
