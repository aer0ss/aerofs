package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.lib.cfg.CfgAbsAuxRoot;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.testlib.AbstractTest;

/**
 * This class is structurally identical to TestUIPostUpdateTasks
 */
public class TestDaemonPostUpdateTasks extends AbstractTest
{
    @Mock CfgDatabase cfgDB;
    @Mock CoreDBCW dbcw;
    @Mock IStoreDatabase sdb;
    @Mock CfgAbsAuxRoot absAuxRoot;

    InMemorySQLiteDBCW idbcw = new InMemorySQLiteDBCW();

    @InjectMocks DaemonPostUpdateTasks dput;

    @Test
    public void shouldBeConsistentWithParam() throws Exception
    {
        /**
         * There is a check in dput.run() that asserts the actual number of post-update tasks is
         * equal to a value defined in Param.java. This test verifies that when adding a task,
         * we also update the value in Param so the assertion doesn't fail. Because the assertion
         * happens in DaemonPostUpdateTasks's constructor, which is called during this test class's
         * construction, no code is needed for this test method.
         */
    }
}
