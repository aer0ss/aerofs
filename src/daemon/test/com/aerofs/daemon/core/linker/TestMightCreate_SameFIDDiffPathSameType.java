package com.aerofs.daemon.core.linker;

import com.aerofs.lib.Util;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;
import static com.aerofs.daemon.core.linker.MightCreateOperations.*;

import org.junit.Before;
import org.junit.Test;

import java.util.EnumSet;

import static junit.framework.Assert.assertNull;


/**
 * Cases: logical file f1 has the same FID with some physical file (specified below)
 *
 * Result: Rename f1 to the physical file's name
 */
public class TestMightCreate_SameFIDDiffPathSameType extends AbstractTestMightCreate
{
    final String namef1 = "f1";
    SOID soidf1;
    SOID soidRoot;

    @Before
    public void setup() throws Exception
    {
        soidf1 = ds.resolveNullable_(new Path(namef1));
        soidRoot = new SOID(soidf1.sidx(), OID.ROOT);
    }

    /**
     * Rename f1 to f2
     */
    @Test
    public void shouldRenameAndMoveAwayConflictWhenDifferentNames() throws Exception
    {
        String namef2 = "f2";
        shouldRenameFromf1(namef2);

        // NB: this test is fucked up: it's supposed to exercise a file move-over
        // but the target path is also in a state of type mismatch...
        // TODO: clean up the test battery...
        verifyOperationExecuted(
                EnumSet.of(Operation.Update, Operation.RenameTarget, Operation.RandomizeFID),
                namef2);
    }

    /**
     * Rename f1 to F1
     */
    @Test
    public void shouldNotMoveAwayConflictWhenDifferentCases() throws Exception
    {
        shouldRenameFromf1(namef1.toUpperCase());

        verifyOperationExecuted(Operation.Update, namef1.toUpperCase());
    }

    /**
     * Rename f1 to a file that does not yet exist in the Directory Service
     */
    @Test
    public void shouldNotMoveAwayConflictWhenNotYetInDirectoryService() throws Exception
    {
        // Assert that this file name is not in the ds. This name was chosen
        // because in AbstractTestMightCreate, it exists as a mocked OS file,
        // but not in the Directory Service
        String nameNew = "f2 (3)";
        assertNull(ds.resolveNullable_(new Path(nameNew)));
        shouldRenameFromf1(nameNew);

        verifyOperationExecuted(Operation.Update, nameNew);
    }

    private void shouldRenameFromf1(String physicalName) throws Exception
    {
        assign(soidf1, dr.getFID(Util.join(pRoot, physicalName)));

        mightCreate(physicalName);
    }
}
