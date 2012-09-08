/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.linker;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNotFound;
import org.junit.Test;

import java.io.IOException;

import static org.mockito.Mockito.when;

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

    protected void mockGetFIDThrowingNotFoundException(String physicalObj)
            throws IOException, ExNotFound
    {
        when(dr.getFIDAndType(Util.join(pRoot, physicalObj))).thenThrow(new ExNotFound());
    }
}
