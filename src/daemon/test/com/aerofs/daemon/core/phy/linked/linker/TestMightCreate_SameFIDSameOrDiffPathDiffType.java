package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.EnumSet;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.*;

/**
 * Case: logical _folder_ f2 and physical file f2 have the same FID
 *
 * Result: move away the logical folder and set its FID to a random value. create a logical file.
 */
public class TestMightCreate_SameFIDSameOrDiffPathDiffType extends AbstractTestMightCreate
{
    SOID soidFolder;

    @Before
    public void setup() throws Exception
    {
        soidFolder = ds.resolveNullable_(mkpath("f2"));

        assign(ds.resolveNullable_(mkpath("f2")), dr.getFID(Util.join(pRoot, "f2")));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                SOID soid = (SOID) invocation.getArguments()[0];
                FID fid = (FID) invocation.getArguments()[1];
                OA oa = ds.getOANullable_(soid);
                when(oa.fid()).thenReturn(fid);
                return null;
            }
        }).when(ds).setFID_(any(SOID.class), any(FID.class), any(Trans.class));
    }

    @Test
    public void shouldSetFIDAndMoveAwayFolderAndCreateFile() throws Exception
    {
        mightCreate("f2");

        verifyOperationExecuted(
                EnumSet.of(Operation.CREATE, Operation.RANDOMIZE_SOURCE_FID, Operation.RENAME_TARGET),
                soidFolder, soidFolder, "f2");
    }
}
