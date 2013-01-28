/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import org.junit.Before;

import java.sql.SQLException;

/**
 * Similar to AbstractTestWithSPDatabase, except that this class automatically begins the
 * transaction before each test. Therefore, no tests in the class's subclasses needs explicity
 * transaction management.
 *
 * Note that both this class and AbstractTestWithSPDatabase cleans up the transaction after each
 * test.
 */
public class AbstractAutoTransactionedTestWithSPDatabase extends AbstractTestWithDatabase
{
    @Before
    public final void setupTransaction()
            throws SQLException
    {
        sqlTrans.begin();
        jedisTrans.begin();
    }
}
