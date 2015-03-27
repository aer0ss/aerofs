package com.aerofs.ui.update.uput;

import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static com.aerofs.lib.cfg.CfgDatabase.UI_POST_UPDATES;
import static org.mockito.Mockito.when;

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
        when(cfgDB.getInt(UI_POST_UPDATES)).thenReturn(0);
    }

    @Test
    public void shouldBeConsistentWithParam() throws Exception
    {
        /**
         * There is a check in the class that asserts the actual number of post-update tasks is
         * equal to a value defined in LibParam.java. This test verifies that when adding a task,
         * we also update the value in LibParam so the assertion doesn't fail. Because the assertion
         * happens in the class's constructor, which is called during this test class's
         * construction, no code is needed for this test method.
         */
    }
}
