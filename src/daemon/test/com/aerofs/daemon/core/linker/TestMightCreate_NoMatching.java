package com.aerofs.daemon.core.linker;

import java.io.IOException;

import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.linker.MightCreate.Result;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.lib.id.SOID;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Case: Physical file "f2 (3)" has no corresponding logical object.
 *
 * Result: Create a logical object.
 */
public class TestMightCreate_NoMatching extends AbstractTestMightCreate
{
    @Test
    public void shouldOnlyCreateNewObject() throws Exception, IOException
    {
        mightCreate("f2 (3)", null);

        verifyZeroInteractions(vu, om);

        verify(oc).create_(eq(Type.FILE), any(SOID.class), eq("f2 (3)"), eq(PhysicalOp.MAP), eq(t));
    }

    @Test
    public void shouldReturn_FILE_onFile() throws Exception
    {
        assertTrue(mightCreate("f2 (3)", null) == Result.FILE);
    }

    @Test
    public void shouldReturn_NEW_FOLDER_onFolder() throws Exception
    {
        assertTrue(mightCreate("d3", null) == Result.NEW_OR_REPLACED_FOLDER);
    }

    @Test
    public void shouldReturn_IGNORED_onIgnored() throws Exception
    {
        assertTrue(mightCreate("ignored", null) == Result.IGNORED);
    }
}
