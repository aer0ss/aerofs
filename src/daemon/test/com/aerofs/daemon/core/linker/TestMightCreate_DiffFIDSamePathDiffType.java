package com.aerofs.daemon.core.linker;

import java.io.IOException;

import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.lib.id.FID;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;
import com.aerofs.base.id.UniqueID;

import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

/**
 * Case: Logical _folder_ f2 has same path as a physical file. At the same time, "f2 (2)" and
 * "f2 (3)" already exist logically or physically.
 *
 * Result: Rename logical f2 to "f2 (4)", and create a new logical f2.
 */
public class TestMightCreate_DiffFIDSamePathDiffType extends AbstractTestMightCreate
{
    SOID soidF2;

    @Before
    public void setup() throws Exception
    {
        soidF2 = ds.resolveNullable_(new Path("f2"));

        FID fidLogical = new FID(UniqueID.generate().getBytes());
        assign(soidF2, fidLogical);
    }

    @Test
    public void shouldRenameExistingObjectAndAvoidNameConflictWithBothLogicalAndPhysicalObjects()
        throws Exception, IOException
    {
        mightCreate("f2", null);

        verifyZeroInteractions(vu);

        verify(om).moveInSameStore_(soidF2, OID.ROOT, "f2 (4)", PhysicalOp.MAP, false, true, t);
    }

    @Test
    public void shouldCreateNewObject() throws Exception, IOException
    {
        mightCreate("f2", null);

        verifyZeroInteractions(vu);

        verify(oc).create_(eq(Type.FILE), any(OID.class), any(SOID.class), eq("f2"),
                eq(PhysicalOp.MAP), eq(t));
    }
}
