package com.aerofs.lib.cfg;

import java.sql.SQLException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.aerofs.testlib.AbstractTest;

public class TestCfgDatabase extends AbstractTest
{
    CfgDatabase db;

    @Before
    public void setup() throws SQLException
    {
        db = new CfgDatabase(true);
        db.init_();
        db.recreateSchema_();
    }

    // there was a bug: calling init_() closes the connection but not existing prepared statements.
    // this caused subsequent set() calls fail. init_() in the new code doesn't close the connection
    // any more.
    @Test
    public void shouldBeAbleToSetAfterCallingInitAgain() throws SQLException
    {
        db.set(CfgDatabase.Key.LAST_LOG_CLEANING, 123);
        db.init_();
        db.set(CfgDatabase.Key.LAST_LOG_CLEANING, 123);
    }

    @After
    public void teardown() throws SQLException
    {
        db.fini_();
    }
}
