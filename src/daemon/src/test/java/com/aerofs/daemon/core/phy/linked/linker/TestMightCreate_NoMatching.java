package com.aerofs.daemon.core.phy.linked.linker;

import java.io.IOException;

import com.aerofs.daemon.core.phy.linked.linker.MightCreate.Result;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.*;

import org.junit.Test;

import static org.junit.Assert.*;

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
        mightCreate("f2 (3)");

        verifyOperationExecuted(Operation.CREATE, "f2 (3)");
    }

    @Test
    public void shouldReturn_FILE_onFile() throws Exception
    {
        assertEquals(Result.FILE, mightCreate("f2 (3)"));
    }

    @Test
    public void shouldReturn_NEW_FOLDER_onFolder() throws Exception
    {
        assertEquals(Result.NEW_OR_REPLACED_FOLDER, mightCreate("d3"));
    }

    @Test
    public void shouldReturn_IGNORED_onIgnored() throws Exception
    {
        assertEquals(Result.IGNORED, mightCreate("ignored"));
    }
}
