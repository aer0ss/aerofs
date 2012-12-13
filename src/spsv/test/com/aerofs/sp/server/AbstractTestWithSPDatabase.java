/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.AppRoot;
import com.aerofs.servlets.lib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servlets.lib.db.SPDatabaseParams;
import com.aerofs.servlets.lib.db.SQLThreadLocalTransaction;
import com.aerofs.testlib.AbstractTest;
import org.junit.After;
import org.junit.Before;
import org.mockito.Spy;

import java.io.IOException;
import java.sql.SQLException;

/**
 * This test class sets up SP database schema before running each test, and clean up
 * transactions afterward if the test fails leaving the transaction open.
 */
public class AbstractTestWithSPDatabase extends AbstractTest
{
    private final SPDatabaseParams dbParams = new SPDatabaseParams();

    // @Spy is needed for subclasses to @InjectMocks
    @Spy protected final SQLThreadLocalTransaction trans =
            new SQLThreadLocalTransaction(dbParams.getProvider());

    @Before
    public final void setupDatabaseSchema()
            throws IOException, ClassNotFoundException, SQLException, InterruptedException
    {
        // This is to workaround Labeling class initialization issues in various derived classes.
        AppRoot.set("/not-exist");

        LocalTestDatabaseConfigurator.initializeLocalDatabase(dbParams);
    }

    @After
    public final void cleanupLeftoverTransaction()
            throws SQLException
    {
        if (trans.isInTransaction()) trans.rollback();
        trans.cleanUp();
    }
}
