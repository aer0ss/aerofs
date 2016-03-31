package com.aerofs.daemon.core.status;

import com.aerofs.daemon.core.net.device.Devices;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSyncStatusPresenceListener extends AbstractSyncStatusTest
{
    SyncStatusPresenceListener listener;

    @Mock Devices devices;

    @Before
    public void before() throws Exception {
        listener = new SyncStatusPresenceListener(propagator, syncStatusOnline, userAndDeviceNames,
                devices);
        syncStatusOnline.set(false);
    }

    @Test
    public void testOnlineOffline() {
        listener.online_(irrelevant);
        assertFalse(syncStatusOnline.get());
        listener.offline_(irrelevant);
        assertFalse(syncStatusOnline.get());
        listener.online_(storageAgentDID1);
        assertTrue(syncStatusOnline.get());
        listener.online_(storageAgentDID2);
        assertTrue(syncStatusOnline.get());
        listener.offline_(storageAgentDID1);
        assertTrue(syncStatusOnline.get());
        listener.offline_(storageAgentDID2);
        assertFalse(syncStatusOnline.get());
    }
}
