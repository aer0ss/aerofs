package com.aerofs.daemon.core.phy.linked.linker;

import java.io.IOException;
import java.util.EnumSet;

import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;
import com.aerofs.ids.UniqueID;

import org.junit.Before;
import org.junit.Test;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.*;

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
        soidF2 = ds.resolveNullable_(mkpath("f2"));

        FID fidLogical = new FID(UniqueID.generate().getBytes());
        assign(soidF2, fidLogical);
    }

    @Test
    public void shouldCreateNewObjectAndRenameExistingObject()
        throws Exception, IOException
    {
        mightCreate("f2");

        verifyOperationExecuted(
                EnumSet.of(Operation.CREATE, Operation.RENAME_TARGET), "f2");
    }
}
