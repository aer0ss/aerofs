/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.core.phy.linked.linker.MightCreate.Result;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExFileNoPerm;
import com.aerofs.lib.ex.ExFileNotFound;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestMightCreate_OtherCases extends AbstractTestMightCreate
{
    @Test
    public void shouldIgnoreNonExistingPhysicalFiles()
        throws Exception
    {
        String physicalObj = "non-existing";
        when(dr.getFIDAndTypeNullable(Util.join(pRoot, physicalObj))).thenThrow(
                new ExFileNotFound(mkpath("dummy/path")));
        assertEquals(Result.IGNORED, mightCreate(physicalObj));
        verify(rocklog).newDefect("mc.fid.notfound");
    }

    @Test
    public void shouldIgnoreInaccessiblePhysicalFiles()
            throws Exception
    {
        String physicalObj = "non-existing";
        when(dr.getFIDAndTypeNullable(Util.join(pRoot, physicalObj))).thenThrow(
                new ExFileNoPerm(new File("dummy/path")));
        assertEquals(Result.IGNORED, mightCreate(physicalObj));
        verify(rocklog).newDefect("mc.fid.noperm");
    }
}
