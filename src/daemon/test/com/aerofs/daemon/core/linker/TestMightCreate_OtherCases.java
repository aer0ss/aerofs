/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.linker;

import com.aerofs.lib.ex.ExNotFound;
import org.junit.Test;

public class TestMightCreate_OtherCases extends AbstractTestMightCreate
{
    @Test(expected = ExNotFound.class)
    public void shouldThrowNotFoundWithNonExistingPhysicalFiles()
        throws Exception
    {
        String physicalObj = "non-existing";
        mockGetFIDThrowingNotFoundException(physicalObj);

        mightCreate(physicalObj, null);
    }
}
