package com.aerofs.daemon.core.status;

import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

public class TestSyncStatusPresenceListener extends AbstractSyncStatusTest
{
    SyncStatusPresenceListener listener;

    @Mock Devices devices;
    @Mock UserAndDeviceNames UserAndDeviceNames;

    DID storageAgentDID1 = DID.generate();
    DID storageAgentDID2 = DID.generate();
    DID irrelevant = DID.generate();
    UserID teamServer = UserID.UNKNOWN_TEAM_SERVER;

    @Before
    public void before() throws Exception {

        doReturn(UserID.UNKNOWN_TEAM_SERVER).when(UserAndDeviceNames)
                .getDeviceOwnerNullable_(storageAgentDID1);
        doReturn(UserID.UNKNOWN_TEAM_SERVER).when(UserAndDeviceNames)
                .getDeviceOwnerNullable_(storageAgentDID2);
        doReturn(UserID.UNKNOWN).when(UserAndDeviceNames).getDeviceOwnerNullable_(irrelevant);

        listener = new SyncStatusPresenceListener(propagator, syncStatusOnline, UserAndDeviceNames,
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
