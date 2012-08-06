package com.aerofs.ui.update.uput;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.*;

import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.testlib.AbstractTest;

/**
 * This class is structurally identical to TestDaemonPostUpdateTasks
 */
public class TestUIPostUpdateTasks extends AbstractTest
{
    @Mock CfgDatabase cfgDB;
    @InjectMocks UIPostUpdateTasks uput;

    @Before
    public void setup()
    {
        when(cfgDB.getInt(Key.UI_POST_UPDATES)).thenReturn(0);
    }

    @Test
    public void shouldBeConsistentWithParam() throws Exception
    {
        // There is a check in uput.run() that asserts the actual number of post-update tasks is
        // equal to a value defined in Param.java. This test verifies that when adding a task,
        // we also update the value in Param so the assertion doesn't fail.

        uput.run();
    }
}
