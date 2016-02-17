package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.phy.ILinker;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.polaris.PolarisAsyncClient;
import com.aerofs.daemon.core.polaris.db.*;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.lib.db.IAliasDatabase;
import com.aerofs.daemon.lib.db.ISIDDatabase;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.db.ver.NativeVersionDatabase;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.cfg.CfgLocalUser;
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
    @Mock CfgUsePolaris cfgUsePolaris;
    DaemonPostUpdateTasks dput;

    @Before
    public void setUp() {
        Injector inj = Guice.createInjector(binder -> {
            binder.bind(CfgDatabase.class).toInstance(cfgDB);
            binder.bind(CfgUsePolaris.class).toInstance(cfgUsePolaris);
            binder.bind(IDBCW.class).toInstance(mock(IDBCW.class));
            binder.bind(CfgLocalDID.class).toInstance(mock(CfgLocalDID.class));
            binder.bind(IOSUtil.class).toInstance(mock(IOSUtil.class));
            binder.bind(InjectableDriver.class).toInstance(mock(InjectableDriver.class));
            // conversion is a monster of a dput, and needs all these dependencies
            binder.bind(DirectoryService.class).toInstance(mock(DirectoryService.class));
            binder.bind(StoreHierarchy.class).toInstance(mock(StoreHierarchy.class));
            binder.bind(ISIDDatabase.class).toInstance(mock(ISIDDatabase.class));
            binder.bind(CfgLocalUser.class).toInstance(mock(CfgLocalUser.class));
            binder.bind(LocalACL.class).toInstance(mock(LocalACL.class));
            binder.bind(TransManager.class).toInstance(mock(TransManager.class));
            binder.bind(NativeVersionDatabase.class).toInstance(mock(NativeVersionDatabase.class));
            binder.bind(MetaChangesDatabase.class).toInstance(mock(MetaChangesDatabase.class));
            binder.bind(RemoteContentDatabase.class).toInstance(mock(RemoteContentDatabase.class));
            binder.bind(ContentFetchQueueDatabase.class).toInstance(mock(ContentFetchQueueDatabase.class));
            binder.bind(ContentChangesDatabase.class).toInstance(mock(ContentChangesDatabase.class));
            binder.bind(ChangeEpochDatabase.class).toInstance(mock(ChangeEpochDatabase.class));
            binder.bind(CentralVersionDatabase.class).toInstance(mock(CentralVersionDatabase.class));
            binder.bind(IAliasDatabase.class).toInstance(mock(IAliasDatabase.class));
            binder.bind(PolarisAsyncClient.class).toInstance(mock(PolarisAsyncClient.class));
            binder.bind(IPhysicalStorage.class).toInstance(mock(IPhysicalStorage.class));
            binder.bind(ILinker.class).toInstance(mock(ILinker.class));
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

    @Test
    public void shouldInjectConversionTasks() throws Exception
    {
        when(cfgUsePolaris.get()).thenReturn(true);
        when(cfgDB.getInt(CfgDatabase.DAEMON_POST_UPDATES))
                .thenReturn(DaemonPostUpdateTasks.firstValid());
        dput.run(true);
    }
}
