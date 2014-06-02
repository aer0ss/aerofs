package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;
import com.aerofs.base.id.UniqueID;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.*;

import org.junit.Test;

/*
 * Case: logical file f1 and f5 has a different FID than physical file f1 and f5.
 *
 * Result: update logical f1 and f5's FID
 */
public class TestMightCreate_DiffFIDSamePathSameType extends AbstractTestMightCreate
{
    @Test
    public void shouldReplaceMismatchedFID() throws Exception
    {
        SOID soidF1 = ds.resolveNullable_(mkpath("f1"));
        FID fidLogical = new FID(UniqueID.generate().getBytes());
        assign(soidF1, fidLogical);

        mightCreate("f1");

        verifyOperationExecuted(Operation.REPLACE, null, soidF1, "f1");
    }

    @Test
    public void shouldReplaceMissingFID() throws Exception
    {
        SOID soidF5 = ds.resolveNullable_(mkpath("f5"));

        mightCreate("f5");

        verifyOperationExecuted(Operation.REPLACE, null, soidF5, "f5");
    }
}
