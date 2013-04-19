package com.aerofs.lib.cfg;

import java.sql.SQLException;

import com.aerofs.base.id.DID;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDatabaseParams;
import com.aerofs.lib.db.dbcw.IDBCW;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.aerofs.testlib.AbstractTest;

public class TestCfgDatabase extends AbstractTest
{
    IDBCW _dbcw;
    CfgDatabase db;

    @Before
    public void setup() throws SQLException
    {
        _dbcw = DBUtil.newDBCW(new IDatabaseParams() {
            @Override
            public boolean isMySQL()
            {
                return false;
            }

            @Override
            public String url()
            {
                return "jdbc:sqlite::memory:";
            }

            @Override
            public boolean sqliteExclusiveLocking()
            {
                return false;
            }

            @Override
            public boolean sqliteWALMode()
            {
                return false;
            }

            @Override
            public boolean autoCommit()
            {
                return true;
            }
        });
        _dbcw.init_();

        db = new CfgDatabase(_dbcw);
        db.recreateSchema_();
        db.set(Key.DEVICE_ID, DID.generate().toStringFormal());
    }

    // there was a bug: calling init_() closes the connection but not existing prepared statements.
    // this caused subsequent set() calls fail. init_() in the new code doesn't close the connection
    // any more.
    @Test
    public void shouldBeAbleToSetAfterCallingInitAgain() throws Exception
    {
        db.set(CfgDatabase.Key.LAST_LOG_CLEANING, 123);
        db.reload();
        db.set(CfgDatabase.Key.LAST_LOG_CLEANING, 123);
    }

    @After
    public void teardown() throws SQLException
    {
        _dbcw.fini_();
    }
}
