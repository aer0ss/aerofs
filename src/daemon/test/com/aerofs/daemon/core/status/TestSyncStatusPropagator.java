package com.aerofs.daemon.core.status;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.PathStatus.PBPathStatus.Sync;

import org.junit.Test;

import java.util.Map;

import static com.aerofs.proto.PathStatus.PBPathStatus.Sync.IN_SYNC;
import static com.aerofs.proto.PathStatus.PBPathStatus.Sync.UNKNOWN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestSyncStatusPropagator extends AbstractSyncStatusTest
{
    @Test
    public void shouldPropagateOutOfSyncThenInSyncInOneTransaction() throws Exception {
        try (Trans trans = transManager.begin_()) {
            assertEquals(IN_SYNC, propagator.getSync_(new SOID(rootSIndex, OID.ROOT)));
            propagator.updateSyncStatus_(baz, false, trans);
            assertEquals(UNKNOWN, propagator.getSync_(baz, trans));
            assertEquals(UNKNOWN, propagator.getSync_(bar, trans));
            assertEquals(UNKNOWN, propagator.getSync_(foo, trans));
            assertEquals(UNKNOWN, propagator.getSync_(new SOID(rootSIndex, OID.ROOT), trans));
            assertEquals(IN_SYNC, propagator.getSync_(bar));
            assertEquals(IN_SYNC, propagator.getSync_(foo));
            assertEquals(IN_SYNC, propagator.getSync_(new SOID(rootSIndex, OID.ROOT)));
            propagator.updateSyncStatus_(qux, false, trans);
            assertEquals(UNKNOWN, propagator.getSync_(qux, trans));
            propagator.updateSyncStatus_(moria, false, trans);
            assertEquals(UNKNOWN, propagator.getSync_(moria, trans));
            assertEquals(UNKNOWN, propagator.getSync_(the, trans));
            assertEquals(UNKNOWN, propagator.getSync_(inside, trans));
            assertEquals(UNKNOWN, propagator.getSync_(deep, trans));
            assertEquals(IN_SYNC, propagator.getSync_(the));
            assertEquals(IN_SYNC, propagator.getSync_(inside));
            assertEquals(IN_SYNC, propagator.getSync_(deep));
            propagator.updateSyncStatus_(baz, true, trans);
            propagator.updateSyncStatus_(qux, true, trans);
            propagator.updateSyncStatus_(moria, true, trans);
            propagator.updateSyncStatus_(mines, true, trans);
            assertEquals(IN_SYNC, propagator.getSync_(new SOID(rootSIndex, OID.ROOT), trans));
            trans.commit_();
        }
        assertEquals(IN_SYNC, propagator.getSync_(new SOID(rootSIndex, OID.ROOT)));
    }

    @Test
    public void shouldPropagateOutOfSyncThenInSyncInSeparateTransactions() throws Exception {
        try (Trans trans = transManager.begin_()) {
            assertEquals(IN_SYNC, propagator.getSync_(new SOID(rootSIndex, OID.ROOT)));
            propagator.updateSyncStatus_(baz, false, trans);
            assertEquals(IN_SYNC, propagator.getSync_(bar));
            assertEquals(IN_SYNC, propagator.getSync_(foo));
            assertEquals(IN_SYNC, propagator.getSync_(new SOID(rootSIndex, OID.ROOT)));
            trans.commit_();
        }
        assertEquals(UNKNOWN, propagator.getSync_(baz));
        assertEquals(UNKNOWN, propagator.getSync_(bar));
        assertEquals(UNKNOWN, propagator.getSync_(foo));
        assertEquals(UNKNOWN, propagator.getSync_(new SOID(rootSIndex, OID.ROOT)));
        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(qux, false, trans);
            assertEquals(IN_SYNC, propagator.getSync_(the));
            assertEquals(IN_SYNC, propagator.getSync_(inside));
            assertEquals(IN_SYNC, propagator.getSync_(deep));
            trans.commit_();
        }
        assertEquals(UNKNOWN, propagator.getSync_(qux));
        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(moria, false, trans);
            trans.commit_();
        }
        assertEquals(UNKNOWN, propagator.getSync_(moria));
        assertEquals(UNKNOWN, propagator.getSync_(the));
        assertEquals(UNKNOWN, propagator.getSync_(inside));
        assertEquals(UNKNOWN, propagator.getSync_(deep));
        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(baz, true, trans);
            trans.commit_();
        }
        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(qux, true, trans);
            propagator.updateSyncStatus_(moria, true, trans);
            trans.commit_();
        }
        assertEquals(IN_SYNC, propagator.getSync_(new SOID(rootSIndex, OID.ROOT)));
    }

    @Test
    public void shouldPropagateOverSharedFolderBoundaries() throws Exception {
        try (Trans trans = transManager.begin_()) {
            assertEquals(IN_SYNC, propagator.getSync_(new SOID(rootSIndex, OID.ROOT)));
            propagator.updateSyncStatus_(mines, false, trans);
            trans.commit_();
        }
        assertEquals(UNKNOWN, propagator.getSync_(mines));
        assertEquals(UNKNOWN, propagator.getSync_(store));
        assertEquals(UNKNOWN, propagator.getSync_(anchor));
        assertEquals(UNKNOWN, propagator.getSync_(new SOID(rootSIndex, OID.ROOT)));
        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(mines, true, trans);
            trans.commit_();
        }
        assertEquals(IN_SYNC, propagator.getSync_(mines));
        assertEquals(IN_SYNC, propagator.getSync_(store));
        assertEquals(IN_SYNC, propagator.getSync_(anchor));
        assertEquals(IN_SYNC, propagator.getSync_(new SOID(rootSIndex, OID.ROOT)));
    }

    @Test
    public void shouldDeleteFromSyncStatusRequestsOnUpdate() throws Exception {
        SOID soid = moria;
        syncStatusRequests.setSyncRequest(soid, 152);
        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(soid, true, trans);
            trans.commit_();
        }
        assertNull(syncStatusRequests.getSyncRequestVersion(soid));
    }

    @Test
    public void shouldInsertIntoOutOfSyncFilesDatabaseWhenFileMarkedOutOfSync() throws Exception {
        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(baz, false, trans);
            trans.commit_();
        }

        int outOfSyncFiles = countOutOfSyncFiles();
        assertEquals(1, outOfSyncFiles);
    }

    @Test
    public void shouldDeleteFromOutOfSyncFilesDatabaseWhenFileMarkedInSync() throws Exception {
        try (Trans trans = transManager.begin_()) {
            outOfSyncDatabase.insert_(baz.sidx(), baz.oid(), trans);
            trans.commit_();
        }

        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(baz, true, trans);
            trans.commit_();
        }

        int outOfSyncFiles = countOutOfSyncFiles();
        assertEquals(0, outOfSyncFiles);
    }

    @Test
    public void shouldNotifyListenersWhenStatusChanges() throws Exception {
        TestListener listener = new TestListener();
        propagator.addListener(listener);
        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(baz, false, trans);
            trans.commit_();
        }
        assertEquals(2, listener.notifications);
        assertEquals(4, listener.filesNotified);
    }

    @Test
    public void shouldNotifyListenersOnInSyncUpdateEvenWhenStatusDoesNotChange() throws Exception {
        TestListener listener = new TestListener();
        propagator.addListener(listener);
        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(baz, true, trans);
            trans.commit_();
        }
        assertEquals(1, listener.notifications);
        assertEquals(1, listener.filesNotified);
    }

    @Test
    public void shouldNotNotifyListenersOnOutSyncUpdateWhenStatusDoesNotChange() throws Exception {
        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(baz, false, trans);
            trans.commit_();
        }

        TestListener listener = new TestListener();
        propagator.addListener(listener);

        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(baz, false, trans);
            trans.commit_();
        }

        assertEquals(0, listener.notifications);
        assertEquals(0, listener.filesNotified);
    }

    @Test
    public void shouldNotifyRootSyncStatus() throws Exception {
        TestListener listener = new TestListener();
        propagator.addListener(listener);

        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(qux, false, trans);
            trans.commit_();
        }
        assertEquals(2, listener.notifications);
        assertEquals(3, listener.filesNotified);
    }

    @Test
    public void shouldForceNotifyListeners() throws Exception {
        TestListener listener = new TestListener();
        propagator.addListener(listener);
        propagator.forceNotifyListeners(directoryService.resolve_(baz), Sync.IN_SYNC);
        assertEquals(1, listener.notifications);
        assertEquals(1, listener.filesNotified);
    }

    private class TestListener implements ISyncStatusListener
    {
        public int notifications = 0;
        public int filesNotified = 0;

        @Override
        public void onStatusChanged_(Map<Path, Sync> updates) {
            filesNotified += updates.size();
            notifications++;
        }
    }
}
