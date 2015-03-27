package com.aerofs.daemon.core.update;

import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.os.IOSUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.testlib.AbstractTest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class is structurally identical to TestUIPostUpdateTasks
 */
public class TestDaemonPostUpdateTasks extends AbstractTest
{
    @Mock CfgDatabase cfgDB;
    DaemonPostUpdateTasks dput;

    @Before
    public void setUp() {
        Injector inj = Guice.createInjector(binder -> {
            binder.bind(CfgDatabase.class).toInstance(cfgDB);
            binder.bind(IDBCW.class).toInstance(mock(IDBCW.class));
            binder.bind(CfgLocalDID.class).toInstance(mock(CfgLocalDID.class));
            binder.bind(IOSUtil.class).toInstance(mock(IOSUtil.class));
            binder.bind(InjectableDriver.class).toInstance(mock(InjectableDriver.class));
        });
        dput = inj.getInstance(DaemonPostUpdateTasks.class);
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

    @Test
    public void shouldInjectTasks() throws Exception
    {
        when(cfgDB.getInt(CfgDatabase.DAEMON_POST_UPDATES))
                .thenReturn(DaemonPostUpdateTasks.firstValid());
        dput.run(true);
    }
}
