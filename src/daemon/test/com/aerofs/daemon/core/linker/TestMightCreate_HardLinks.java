/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.linker;

import com.aerofs.daemon.core.linker.MightCreate.Result;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class TestMightCreate_HardLinks extends AbstractTestMightCreate
{
    final String fName1 = "f1";
    final String fName2 = "F1";
    final String fName3 = "f2";
    FIDAndType fFNT;

    final String dirname2 = "d4";
    final String dirName1 = "d3";
    FIDAndType dirFNT;

    @Before
    public void setup() throws Exception
    {
        // setup files with the same FID
        fFNT = dr.getFIDAndType(Util.join(pRoot, fName1));
        when(dr.getFIDAndType(Util.join(pRoot, fName2))).thenReturn(fFNT);
        when(dr.getFIDAndType(Util.join(pRoot, fName3))).thenReturn(fFNT);

        // setup folders with the same FID
        dirFNT = dr.getFIDAndType(Util.join(pRoot, dirName1));
        when(dr.getFIDAndType(Util.join(pRoot, dirname2))).thenReturn(dirFNT);
    }

    @Test
    public void logicalFileShouldRemainInFileSystem() throws Exception
    {
        SOID fSOID = ds.resolveNullable_(new Path(fName1));
        assign(fSOID, fFNT._fid);

        assertTrue(mightCreate(fName1, fName1) == Result.FILE);
        assertTrue(mightCreate(fName2, fName1) == Result.IGNORED);
        assertTrue(mightCreate(fName3, fName1) == Result.IGNORED);
    }

    @Test
    public void createOnlyOneFileIfNotInDS() throws Exception
    {
        when(ds.resolveNullable_(new Path(fName1))).thenReturn(null);
        when(ds.resolveNullable_(new Path(fName2))).thenReturn(null);
        when(ds.resolveNullable_(new Path(fName3))).thenReturn(null);

        assertTrue(mightCreate(fName1, null) == Result.FILE);
        assertTrue(mightCreate(fName2, null) == Result.IGNORED);
        assertTrue(mightCreate(fName3, null) == Result.IGNORED);
    }

    @Test
    public void logicalFolderShouldRemainInFileSystem() throws Exception
    {

    }

    @Test
    public void createOnlyOneFolderIfNotInDirectoryService() throws Exception
    {

    }

}
