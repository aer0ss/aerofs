/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import org.junit.Before;

import java.sql.SQLException;

/**
 * Similar to AbstractTestWithSPDatabase, except that this class automatically begins the
 * transaction before each test.
 */
public class AbstractAutoTransactionedTestWithSPDatabase extends AbstractTestWithSPDatabase
{
    @Before
    public final void setupTransaction()
            throws SQLException
    {
        trans.begin();
    }
}
