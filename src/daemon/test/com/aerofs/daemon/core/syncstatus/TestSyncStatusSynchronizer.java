package com.aerofs.daemon.core.syncstatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.lib.db.IActivityLogDatabase;
import com.aerofs.daemon.lib.db.ISyncStatusDatabase;
import com.aerofs.daemon.lib.db.SyncStatusDatabase;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.db.ver.INativeVersionDatabase;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.testlib.AbstractTest;

public class TestSyncStatusSynchronizer extends AbstractTest {
    InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();

    @Mock CoreQueue q;
    @Mock TC tc;
    @Mock TransManager tm;
    @Spy ISyncStatusDatabase ssdb = new SyncStatusDatabase(dbcw.mockCoreDBCW());
    @Mock IActivityLogDatabase aldb;
    @Mock INativeVersionDatabase nvdb;
    @Mock SIDMap sidmap;

    @InjectMocks SyncStatusSynchronizer sync;

    @Before
    public void setup() throws Exception
    {
        dbcw.init_();
    }

    @After
    public void tearDown() throws Exception
    {
        dbcw.fini_();
    }

    @Test
    public void shouldNotSchedulePullOnOlderEpoch()
    {
        // TODO (huguesb):
    }

    @Test
    public void shouldSchedulePullOnNewerEpoch()
    {
        // TODO (huguesb):
    }

    @Test
    public void shouldPushVersionForBootstrap()
    {
        // TODO (huguesb):
    }

    @Test
    public void shouldPushVersionForActivity()
    {
        // TODO (huguesb):
    }
}
