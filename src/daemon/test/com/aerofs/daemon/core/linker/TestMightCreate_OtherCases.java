/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.linker;

import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExFileNotFound;
import org.junit.Test;

import static org.mockito.Mockito.when;

public class TestMightCreate_OtherCases extends AbstractTestMightCreate
{
    @Test(expected = ExFileNotFound.class)
    public void shouldThrowFileNotFoundWithNonExistingPhysicalFiles()
        throws Exception
    {
        String physicalObj = "non-existing";
        mockGetFIDThrowingFileNotFoundException(physicalObj);
        mightCreate(physicalObj);
    }

    protected void mockGetFIDThrowingFileNotFoundException(String physicalObj)
            throws Exception
    {
        when(dr.getFIDAndType(Util.join(pRoot, physicalObj))).thenThrow(
                new ExFileNotFound(new Path("dummy/path")));
    }
}
