package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreDBCW;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.*;

import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.testlib.AbstractTest;

import java.sql.SQLException;

/**
 * This class is structurally identical to TestUIPostUpdateTasks
 */
public class TestDaemonPostUpdateTasks extends AbstractTest
{
    @Mock CfgDatabase cfgDB;
    @Mock CoreDBCW dbcw;
    @InjectMocks DaemonPostUpdateTasks dput;

    @Before
    public void setup()
            throws SQLException
    {
        when(cfgDB.getInt(Key.DAEMON_POST_UPDATES)).thenReturn(0);
        when(dbcw.get()).then(RETURNS_DEEP_STUBS);
    }

    @Test
    public void shouldBeConsistentWithParam() throws Exception
    {
        // There is a check in dput.run() that asserts the actual number of post-update tasks is
        // equal to a value defined in Param.java. This test verifies that when adding a task,
        // we also update the value in Param so the assertion doesn't fail.

        dput.run();
    }
}
