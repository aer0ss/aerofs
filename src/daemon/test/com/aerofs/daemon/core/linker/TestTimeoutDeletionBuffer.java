package com.aerofs.daemon.core.linker;

import java.sql.SQLException;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.linker.TimeoutDeletionBuffer.Holder;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.testlib.AbstractTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.*;

import static junit.framework.Assert.*;

public class TestTimeoutDeletionBuffer extends AbstractTest
{
    @Mock CoreScheduler scheduler;
    @Mock DirectoryService ds;
    @Mock ObjectDeleter od;
    @Mock OA oa;
    @InjectMocks TimeoutDeletionBuffer delBuffer;
    Holder h;

    @Mock Trans t;
    SOID soid = new SOID(new SIndex(1), new OID(UniqueID.generate()));

    @Before
    public void setup() throws SQLException
    {
        h = delBuffer.newHolder();
        when(ds.hasOA_(any(SOID.class))).thenReturn(true);
        when(ds.hasAliasedOA_(any(SOID.class))).thenReturn(true);
        when(ds.getOANullable_(any(SOID.class))).thenReturn(oa);
        when(oa.name()).thenReturn("name");
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
        SOID [] expectedDeletableSOIDs = new SOID[] {
                new SOID(new SIndex(2), new OID(UniqueID.generate())),
                new SOID(new SIndex(3), new OID(UniqueID.generate()))
        };

        SOID [] expectedHeldSOIDs = new SOID[] {
                new SOID(new SIndex(4), new OID(UniqueID.generate())),
                new SOID(new SIndex(5), new OID(UniqueID.generate()))
        };

        SOID [] expectedTooRecentSOIDs = new SOID[] {
                new SOID(new SIndex(6), new OID(UniqueID.generate())),
                new SOID(new SIndex(7), new OID(UniqueID.generate()))
        };


        for (SOID s : expectedDeletableSOIDs) delBuffer.add_(s);
        for (SOID s : expectedHeldSOIDs) h.hold_(s);

        // Sleep for some short timeout so that some SOIDs are not deleted as their timeout has
        // not passed
        final long timeoutDelay = 200;
        Thread.sleep(timeoutDelay);
        for (SOID s : expectedTooRecentSOIDs) delBuffer.add_(s);

        // Tell the executor that enough time has passed to delete the first 4 objects
        final long mockExecuteTime = System.currentTimeMillis() + TimeoutDeletionBuffer.TIMEOUT -
                                     timeoutDelay;
        final boolean remainingUnheld = delBuffer.executeDeletion_(mockExecuteTime, t);

        // There should be some objects remaining that are unheld, but were not deleted as the
        // timeout did not elapse.
        assertTrue(remainingUnheld);

        // We should see the expected SOIDs were deleted
        for (SOID s : expectedDeletableSOIDs) {
            verify(od).delete_(eq(s), any(PhysicalOp.class), any(SID.class), eq(t));
        }

        // The other SOIDs should *not* be deleted due to being held or not enough time passed
        for (SOID s : expectedHeldSOIDs) {
            verify(od, never()).delete_(eq(s), any(PhysicalOp.class), any(SID.class), eq(t));
        }
        for (SOID s : expectedTooRecentSOIDs) {
            verify(od, never()).delete_(eq(s), any(PhysicalOp.class), any(SID.class), eq(t));
        }
    }

}
