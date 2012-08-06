package com.aerofs.daemon.core.linker;

import com.aerofs.lib.Util;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.UniqueID;

import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

/*
 * Case: logical file f1 and f5 has a different FID than physical file f1 and f5.
 *
 * Result: update logical f1 and f5's FID
 */
public class TestMightCreate_DiffFIDSamePathSameType extends AbstractTestMightCreate
{
    SOID soidF1;

    @Before
    public void setup() throws Exception
    {
        soidF1 = ds.resolveNullable_(new Path("f1"));
        FID fidLogical = new FID(UniqueID.generate().getBytes());
        assign(soidF1, fidLogical);

        // f5 has a null FID because its master branch is absent
    }

    @Test
    public void shouldOnlyReplace() throws Exception
    {
        mightCreate("f1", "f1");

        verifyZeroInteractions(cm, om, oc);
        FID fidPhysical = dr.getFID(Util.join(pRoot, "f1"));
        verify(ds).setFID_(soidF1, fidPhysical, t);
    }

    @Test
    public void shouldCreateMasterCAAndModifyContentIfMasterCAIsNotFound() throws Exception
    {
        mightCreate("f5", "f5");

        verifyZeroInteractions(om, oc);

        SOID soidF5 = ds.resolveNullable_(new Path("f5"));
        verify(ds).createCA_(soidF5, KIndex.MASTER, t);
        verify(cm).atomicWrite_(new SOCKID(soidF5, CID.CONTENT, KIndex.MASTER), t);
    }
}
