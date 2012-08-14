package com.aerofs.daemon.core.linker;

import com.aerofs.daemon.core.linker.MightCreate.Result;
import com.aerofs.lib.Util;
import com.aerofs.lib.Path;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Case: logical file f1 and physical file f1 are consistent with each other.
 */
public class TestMightCreate_SameFIDSamePathSameType extends AbstractTestMightCreate
{
    @Before
    public void setup() throws Exception
    {
        assign(ds.resolveNullable_(new Path("f1")), dr.getFID(Util.join(pRoot, "f1")));
        assign(ds.resolveNullable_(new Path("d4")), dr.getFID(Util.join(pRoot, "d4")));
    }

    @Test
    public void shouldDoNothing() throws Exception
    {
        mightCreate("f1", "f1");

        verifyZeroInteractions(vu, om, oc);
    }

    @Test
    public void shouldReturn_FILE_onFile() throws Exception
    {
        assertTrue(mightCreate("f1", "f1") == Result.FILE);
    }

    @Test
    public void shouldReturn_OLD_FOLDER_onFolder() throws Exception
    {
        assertTrue(mightCreate("d4", "d4") == Result.EXISTING_FOLDER);
    }
}
