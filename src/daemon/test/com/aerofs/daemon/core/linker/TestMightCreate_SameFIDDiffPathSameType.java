package com.aerofs.daemon.core.linker;

import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;

import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;
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

        // This rename should exercise the "move away conflict" path
        SOID soidF2 = ds.resolveNullable_(new Path(namef2));
        verify(om).moveInSameStore_(eq(soidF2), eq(soidRoot.oid()), anyString(),
                any(PhysicalOp.class), anyBoolean(), anyBoolean(), eq(t));
    }

    /**
     * Rename f1 to F1
     */
    @Test
    public void shouldNotMoveAwayConflictWhenDifferentCases() throws Exception
    {
        shouldRenameFromf1(namef1.toUpperCase());

        verify(om, never()).moveInSameStore_(eq(soidf1), eq(soidRoot.oid()), anyString(),
                any(PhysicalOp.class), anyBoolean(), anyBoolean(), eq(t));
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

        verify(om, never()).moveInSameStore_(any(SOID.class), any(OID.class), anyString(),
                any(PhysicalOp.class), anyBoolean(), anyBoolean(), eq(t));
    }

    private void shouldRenameFromf1(final String physicalName) throws Exception
    {
        assign(soidf1, dr.getFID(Util.join(pRoot, physicalName)));

        mightCreate(physicalName, namef1);

        verifyZeroInteractions(vu, oc);

        verify(hdmo).move_(soidf1, soidRoot, physicalName, PhysicalOp.MAP, t);
    }
}
