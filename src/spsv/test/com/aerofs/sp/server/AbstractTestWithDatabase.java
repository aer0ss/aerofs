/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server;

import com.aerofs.servlets.lib.db.BifrostDatabaseParams;
import com.aerofs.servlets.lib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servlets.lib.db.SPDatabaseParams;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.testlib.AbstractBaseTest;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.Spy;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * This test class sets up SP database schema before running each test, and clean up
 * transactions afterward if the test fails leaving the transaction open.
 */
public class AbstractTestWithDatabase extends AbstractBaseTest
{
    private static final SPDatabaseParams dbParams = new SPDatabaseParams();

    // @Spy is needed for subclasses to @InjectMocks
    @Spy protected final SQLThreadLocalTransaction sqlTrans =
            new SQLThreadLocalTransaction(dbParams.getProvider());

    protected PooledJedisConnectionProvider jedisProvider =
            new PooledJedisConnectionProvider();
    @Spy protected JedisThreadLocalTransaction jedisTrans =
            new JedisThreadLocalTransaction(jedisProvider);

    // NB: make sure the name is not shadowed in a subclass or the method won't run...
    @BeforeClass
    public static void AbstractTestWithDatabase_commonSetup() throws Exception
    {
        LocalTestDatabaseConfigurator.resetDB(new BifrostDatabaseParams());
        LocalTestDatabaseConfigurator.initializeLocalDatabase(dbParams);
    }

    @Before
    public void dbSetup() throws Exception
    {
        sqlTrans.begin();
        try (Statement s = sqlTrans.getConnection().createStatement()) {
            for (String table : SPDatabaseParams.TABLES) {
                s.execute("delete from sp_" + table);
            }
        }
        sqlTrans.commit();

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
