/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.AppRoot;
import com.aerofs.servlets.lib.db.ExDbInternal;
import com.aerofs.servlets.lib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servlets.lib.db.SPDatabaseParams;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.testlib.AbstractTest;
import org.junit.After;
import org.junit.Before;
import org.mockito.Spy;

import java.sql.SQLException;

/**
 * This test class sets up SP database schema before running each test, and clean up
 * transactions afterward if the test fails leaving the transaction open.
 */
public class AbstractTestWithDatabase extends AbstractTest
{
    private final SPDatabaseParams dbParams = new SPDatabaseParams();

    // @Spy is needed for subclasses to @InjectMocks
    @Spy protected final SQLThreadLocalTransaction sqlTrans =
            new SQLThreadLocalTransaction(dbParams.getProvider());

    protected PooledJedisConnectionProvider jedisProvider =
            new PooledJedisConnectionProvider();
    @Spy protected JedisThreadLocalTransaction jedisTrans =
            new JedisThreadLocalTransaction(jedisProvider);

    @Before
    public void setupAbstractTestWithDatabaseSchema()
            throws Exception
    {
        // This is to workaround Labeling class initialization issues in various derived classes.
        AppRoot.set("/not-exist");

        LocalTestDatabaseConfigurator.initializeLocalDatabase(dbParams);
    }

    @Before
    public void setupAbstractTestWithDatabaseJedis()
            throws ExDbInternal
    {
        jedisProvider.init_("localhost", 6379, null);

        jedisTrans.begin();
        jedisTrans.get().flushAll();
        jedisTrans.commit();
    }

    @After
    public final void cleanupLeftoverTransaction()
            throws SQLException
    {
        if (sqlTrans.isInTransaction()) sqlTrans.rollback();
        sqlTrans.cleanUp();

        if (jedisTrans.isInTransaction()) jedisTrans.rollback();
        jedisTrans.cleanUp();
    }
}
