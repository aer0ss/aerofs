package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.core.phy.linked.linker.MightCreate.Result;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.*;
import com.aerofs.lib.Util;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Case: logical file f1 and physical file f1 are consistent with each other.
 */
public class TestMightCreate_SameFIDSamePathSameType extends AbstractTestMightCreate
{
    @Before
    public void setup() throws Exception
    {
        assign(ds.resolveNullable_(mkpath("f1")), dr.getFID(Util.join(pRoot, "f1")));
        assign(ds.resolveNullable_(mkpath("d4")), dr.getFID(Util.join(pRoot, "d4")));
    }

    @Test
    public void shouldUpdate() throws Exception
    {
        mightCreate("f1");
        verifyOperationExecuted(Operation.UPDATE, "f1");
    }

    @Test
    public void shouldReturn_FILE_onFile() throws Exception
    {
        assertEquals(Result.FILE, mightCreate("f1"));
    }

    @Test
    public void shouldReturn_OLD_FOLDER_onFolder() throws Exception
    {
        assertEquals(Result.EXISTING_FOLDER, mightCreate("d4"));
    }
}
