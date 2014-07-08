package com.aerofs.daemon.core.phy.linked.linker;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;
import java.util.SortedMap;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.linked.RepresentabilityHelper;
import com.aerofs.daemon.core.phy.linked.linker.TimeoutDeletionBuffer.DeletionStatus;
import com.aerofs.daemon.core.phy.linked.linker.TimeoutDeletionBuffer.Holder;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.lib.event.IEvent;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.rocklog.RockLog;
import com.aerofs.testlib.AbstractTest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.*;

import static org.junit.Assert.*;

public class TestTimeoutDeletionBuffer extends AbstractTest
{
    @Mock CoreScheduler scheduler;
    @Mock DirectoryService ds;
    @Mock ObjectDeleter od;
    @Mock OA oa;
    @Mock LinkerRootMap lrm;
    @Mock RockLog rocklog;
    @Mock RepresentabilityHelper rh;
    @Mock IgnoreList il;
    @Mock InjectableFile.Factory factFile;
    @InjectMocks TimeoutDeletionBuffer delBuffer;
    Holder h;

    @Mock Trans t;
    SID rootSID = SID.generate();
    SOID soid = new SOID(new SIndex(1), new OID(UniqueID.generate()));

    @Before
    public void setup() throws SQLException
    {
        h = delBuffer.newHolder();
        when(ds.getOA_(any(SOID.class))).thenReturn(oa);
        when(ds.getOANullable_(any(SOID.class))).thenReturn(oa);
        when(ds.getAliasedOANullable_(any(SOID.class))).thenReturn(oa);
        when(oa.name()).thenReturn("name");
        when(ds.resolve_(oa)).thenReturn(new ResolvedPath(rootSID, ImmutableList.of(soid), ImmutableList.of("name")));
        when(lrm.absRootAnchor_(rootSID)).thenReturn("/AeroFS");
    }

    private static enum ObjectFlag
    {
        NRO,
        NO_CONTENT
    }

    SOID mockObject(ObjectFlag flag) throws SQLException
    {
        SOID soid = new SOID(new SIndex(1), OID.generate());
        OA oa = mock(OA.class);
        when(ds.getOA_(eq(soid))).thenReturn(oa);
        when(ds.getOANullable_(eq(soid))).thenReturn(oa);
        when(ds.getAliasedOANullable_(eq(soid))).thenReturn(oa);
        when(oa.name()).thenReturn("name");
        SortedMap<KIndex, CA> cas = Maps.newTreeMap();
        if (flag == ObjectFlag.NO_CONTENT) {
            when(oa.isFile()).thenReturn(true);
        } else {
            CA ca = mock(CA.class);
            cas.put(KIndex.MASTER, ca);
            when(rh.isNonRepresentable_(oa)).thenReturn(flag == ObjectFlag.NRO);
        }
        when(oa.casNoExpulsionCheck()).thenReturn(cas);
        return soid;
    }

    @Test
    public void shouldScheduleADeletionWhenHeldObjectIsReleased()
    {
        h.hold_(soid);
        h.releaseAll_();

        verify(scheduler).schedule(any(IEvent.class), anyInt());
    }

    @Test
    public void shouldScheduleOnlyOneDeletionWhenSameObjectAddedTwice()
    {
        delBuffer.add_(soid);
        delBuffer.add_(soid);

        verify(scheduler, times(1)).schedule(any(IEvent.class), anyInt());
    }

    @Test
    public void shouldNotScheduleADeletionWhenHeldObjectWasRemovedBeforeRelease()
    {
        h.hold_(soid);
        delBuffer.remove_(soid);
        h.releaseAll_();

        shouldNotScheduleADeletion();
    }

    @Test
    public void shouldNotScheduleADeletionWhenHeldObjectIsAdded()
    {
        h.hold_(soid);
        delBuffer.add_(soid);

        shouldNotScheduleADeletion();
    }

    private void shouldNotScheduleADeletion()
    {
        verify(scheduler, never()).schedule(any(IEvent.class), anyInt());
    }

    @Test
    public void shouldDeleteOnlyUnheldObjectsWhoseTimeoutHasPassed() throws Exception
    {
        Set<SOID> expectedDeletableSOIDs = ImmutableSet.of(
                new SOID(new SIndex(2), OID.generate()),
                new SOID(new SIndex(3), OID.generate())
        );

        Set<SOID> expectedHeldSOIDs = ImmutableSet.of(
                new SOID(new SIndex(4), OID.generate()),
                new SOID(new SIndex(5), OID.generate())
        );

        Set<SOID> expectedTooRecentSOIDs = ImmutableSet.of(
                new SOID(new SIndex(6), OID.generate()),
                new SOID(new SIndex(7), OID.generate())
        );

        Set<SOID> expectedShouldNotDelete = ImmutableSet.of(
                mockObject(ObjectFlag.NO_CONTENT),
                mockObject(ObjectFlag.NRO)
        );

        for (SOID s : expectedDeletableSOIDs) delBuffer.add_(s);
        for (SOID s : expectedHeldSOIDs) h.hold_(s);
        for (SOID s : expectedShouldNotDelete) delBuffer.add_(s);

        // Sleep for some short timeout so that some SOIDs are not deleted as their timeout has
        // not passed
        final long timeoutDelay = 200;
        Thread.sleep(timeoutDelay);
        for (SOID s : expectedTooRecentSOIDs) delBuffer.add_(s);

        // Tell the executor that enough time has passed to delete the first 4 objects
        final long mockExecuteTime = System.currentTimeMillis() + TimeoutDeletionBuffer.TIMEOUT -
                                     timeoutDelay;
        final DeletionStatus remainingUnheld = delBuffer.executeDeletion_(mockExecuteTime, t);

        // There should be some objects remaining that are unheld, but were not deleted as the
        // timeout did not elapse.
        assertTrue(remainingUnheld == DeletionStatus.RESCHEDULE);

        // We should see the expected SOIDs were deleted
        for (SOID s : expectedDeletableSOIDs) {
            verify(od).delete_(eq(s), any(PhysicalOp.class), eq(t));
        }

        // The other SOIDs should *not* be deleted due to being held or not enough time passed
        for (SOID s : expectedHeldSOIDs) verifyNotDeleted(s);
        for (SOID s : expectedTooRecentSOIDs) verifyNotDeleted(s);
        for (SOID s : expectedShouldNotDelete) verifyNotDeleted(s);
    }

    private void verifyNotDeleted(SOID s) throws Exception
    {
        verify(od, never()).deleteAndEmigrate_(eq(s), any(PhysicalOp.class), any(SID.class),
                any(Trans.class));
        verify(od, never()).delete_(eq(s), any(PhysicalOp.class), any(Trans.class));
    }
}
