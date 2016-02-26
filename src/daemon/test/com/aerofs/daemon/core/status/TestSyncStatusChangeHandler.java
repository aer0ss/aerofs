package com.aerofs.daemon.core.status;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.polaris.db.ContentFetchQueueDatabase;
import com.aerofs.daemon.core.polaris.db.ContentFetchQueueWrapper;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.sql.SQLException;

import static com.aerofs.lib.id.KIndex.MASTER;
import static com.aerofs.proto.PathStatus.PBPathStatus.Sync.IN_SYNC;
import static com.aerofs.proto.PathStatus.PBPathStatus.Sync.UNKNOWN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

public class TestSyncStatusChangeHandler extends AbstractSyncStatusTest
{
    SyncStatusChangeHandler changeHandler;
    @Mock ContentFetchQueueDatabase cfqdb;
    @Mock ContentFetchQueueWrapper cfqw;

    @Before
    public void before() throws Exception {
        doReturn(false).when(cfqdb).exists_(any(), any());
        changeHandler = new SyncStatusChangeHandler(propagator, directoryService, cfqdb, cfqw);
    }

    @Test
    public void shouldSetPathOOSWhenContentCreatedThenParentInSyncWhenExpelled() throws Exception {
        try (Trans trans = transManager.begin_()) {
            SOID mines = new SOID(rootSIndex, OID.generate());
            directoryService.createOA_(OA.Type.FILE, mines.sidx(), mines.oid(), the.oid(), "mines",
                    trans);
            directoryService.createCA_(mines, MASTER, trans);
            assertEquals(UNKNOWN, propagator.getSync_(mines));
            assertEquals(UNKNOWN, propagator.getSync_(the, trans));
            directoryService.setExpelled_(mines, true, trans);
            assertEquals(IN_SYNC, propagator.getSync_(the, trans));
            trans.commit_();
        }
        assertEquals(IN_SYNC, propagator.getSync_(the));
    }

    @Test
    public void shouldSetPathOOSWhenContentModifiedThenInSyncWhenDeleted() throws Exception {
        try (Trans trans = transManager.begin_()) {
            directoryService.setCA_(new SOKID(baz, KIndex.MASTER), 0, System.currentTimeMillis(),
                    ContentHash.EMPTY, trans);
            assertEquals(UNKNOWN, propagator.getSync_(baz));
            assertEquals(UNKNOWN, propagator.getSync_(bar, trans));
            directoryService.setOAParentAndName_(directoryService.getOA_(baz),
                    directoryService.getOA_(new SOID(rootSIndex, OID.TRASH)), "baz", trans);
            assertEquals(IN_SYNC, propagator.getSync_(bar, trans));
            trans.commit_();
        }
        assertEquals(IN_SYNC, propagator.getSync_(bar));
    }

    @Test
    public void shouldSetPathOOSWhenContentDeletedAdjustPropagationWhenMoved() throws Exception {
        try (Trans trans = transManager.begin_()) {
            directoryService.deleteCA_(baz, KIndex.MASTER, trans);
            assertEquals(UNKNOWN, propagator.getSync_(baz));
            assertEquals(UNKNOWN, propagator.getSync_(bar, trans));
            directoryService.setOAParentAndName_(directoryService.getOA_(baz),
                    directoryService.getOA_(the), "bar", trans);

            // outside the trans, the new status is not visible
            assertEquals(IN_SYNC, propagator.getSync_(the));
            trans.commit_();
        }
        assertEquals(UNKNOWN, propagator.getSync_(the));
    }

    @Test
    public void shouldSetParentInSyncWhenFolderExpelledThenIgnoreExpelledChild() throws SQLException {
        try (Trans trans = transManager.begin_()) {
            directoryService.setCA_(new SOKID(moria, KIndex.MASTER), 0, System.currentTimeMillis(),
                    ContentHash.EMPTY, trans);
            assertEquals(UNKNOWN, propagator.getSync_(moria));
            assertEquals(UNKNOWN, propagator.getSync_(the, trans));
            assertEquals(UNKNOWN, propagator.getSync_(inside, trans));
            assertEquals(UNKNOWN, propagator.getSync_(deep, trans));
            directoryService.setExpelled_(inside, true, trans);
            assertEquals(IN_SYNC, propagator.getSync_(deep, trans));
            assertEquals(UNKNOWN, propagator.getSync_(inside, trans));
            directoryService.deleteCA_(moria, KIndex.MASTER, trans);
            assertEquals(IN_SYNC, propagator.getSync_(deep, trans));
            assertEquals(UNKNOWN, propagator.getSync_(inside, trans));
            propagator.updateSyncStatus_(moria, true, trans);
            trans.commit_();
        }
        assertEquals(IN_SYNC, propagator.getSync_(deep));
    }

    @Test
    public void shouldReadjustSyncStatusWhenReadmittingFolder() throws SQLException {
        try (Trans trans = transManager.begin_()) {
            propagator.updateSyncStatus_(moria, false, trans);
            trans.commit_();
        }

        assertEquals(UNKNOWN, propagator.getSync_(deep));
        assertEquals(UNKNOWN, propagator.getSync_(inside));

        try (Trans trans = transManager.begin_()) {
            directoryService.setExpelled_(inside, true, trans);
            trans.commit_();
        }

        assertEquals(IN_SYNC, propagator.getSync_(deep));

        try (Trans trans = transManager.begin_()) {
            directoryService.setExpelled_(inside, false, trans);
            trans.commit_();
        }

        assertEquals(UNKNOWN, propagator.getSync_(deep));
        assertEquals(UNKNOWN, propagator.getSync_(inside));
    }

    @Test
    public void shouldCorrectlyPropagateDeletionsInOneTransaction() throws Exception {
        try (Trans trans = transManager.begin_()) {
            assertEquals(IN_SYNC, propagator.getSync_(new SOID(rootSIndex, OID.ROOT)));
            propagator.updateSyncStatus_(baz, false, trans);
            propagator.updateSyncStatus_(bax, false, trans);
            propagator.updateSyncStatus_(bay, false, trans);
            propagator.updateSyncStatus_(baw, false, trans);
            propagator.updateSyncStatus_(moria, false, trans);
            trans.commit_();
        }

        OA oa = directoryService.getOA_(bar);
        assertEquals(5, oa.oosChildren());
        assertFalse(oa.synced());

        try (Trans trans = transManager.begin_()) {
            directoryService.setOAParentAndName_(directoryService.getOA_(moria),
                    directoryService.getOA_(new SOID(rootSIndex, OID.TRASH)), "moria", trans);
            directoryService.setOAParentAndName_(directoryService.getOA_(the),
                    directoryService.getOA_(new SOID(rootSIndex, OID.TRASH)), "the", trans);
            directoryService.setOAParentAndName_(directoryService.getOA_(inside),
                    directoryService.getOA_(new SOID(rootSIndex, OID.TRASH)), "inside", trans);
            directoryService.setOAParentAndName_(directoryService.getOA_(deep),
                    directoryService.getOA_(new SOID(rootSIndex, OID.TRASH)), "deep", trans);
            trans.commit_();
        }

        oa = directoryService.getOA_(bar);
        assertEquals(4, oa.oosChildren());
        assertFalse(oa.synced());
    }
}
