/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.linker;

import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.linker.MightCreate.Result;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;
import static org.junit.Assert.assertEquals;

public class TestMightCreate_HardLinks extends AbstractTestMightCreate
{
    final String fName1 = "f1";
    final String fName2 = "f5";
    final String fName3 = "f2";
    FIDAndType fFNT;                ///< FID shared by all three files above
    final String nonExistingDirName = "d3";
    final String existingDirName    = "d4";    ///< "d4" is already stored in logicRoot
    FIDAndType dirFNT;              ///< FID shared by both directories above


    @Before
    public void setup() throws Exception
    {
        // Simulate hard link between file names fName1-3
        // by setting the same FID {@code fFNT} to all of them
        fFNT = dr.getFIDAndType(Util.join(pRoot, fName1));
        when(dr.getFIDAndType(Util.join(pRoot, fName2))).thenReturn(fFNT);
        when(dr.getFIDAndType(Util.join(pRoot, fName3))).thenReturn(fFNT);

        // Simulate hard link between the directory names nonExistingDirName, existingDirName
        // by settting the same FID {@code dirFNT} to both of them
        dirFNT = dr.getFIDAndType(Util.join(pRoot, existingDirName));
        when(dr.getFIDAndType(Util.join(pRoot, nonExistingDirName))).thenReturn(dirFNT);
    }

    @Test
    public void shouldKeepOneButIgnoreOtherFilesIfSOIDInDB() throws Exception
    {
        // fName1 is part of logicRoot in AbstractTestMightCreate so fSOID can not be null
        SOID fSOID = ds.resolveNullable_(new Path(fName1));
        assign(fSOID, fFNT._fid);

        assertEquals(Result.FILE, mightCreate(fName1, fName1));
        assertEquals(Result.IGNORED, mightCreate(fName2, fName1));
        assertEquals(Result.IGNORED, mightCreate(fName3, fName1));
    }

    @Test
    public void shouldCreateOneFileAndThenIgnoreOthersIfSOIDNotInDB() throws Exception
    {
        // fName1 is part of logicRoot in AbstractTestMightCreate so fSOID can not be null
        SOID fSOID = ds.resolveNullable_(new Path(fName1));
        SOID soidRoot = new SOID(fSOID.sidx(), OID.ROOT);

        // Simulate fName1's creation in the following command.
        when(ds.resolveNullable_(new Path(fName1))).thenReturn(null);

        assertEquals(Result.FILE, mightCreate(fName1, null));

        // Verify the file is created and set the DS to return the correct path
        // The DS now contains an entry for the (fSOID, fFNT._fid)
        // And the path is also stored now in the DS.
        verify(oc).create_(Type.FILE, soidRoot, fName1, PhysicalOp.MAP, t);

        assign(fSOID, fFNT._fid);
        when(ds.resolveNullable_(fSOID)).thenReturn(new Path(fName1));

        assertEquals(Result.IGNORED, mightCreate(fName2, null));
        verify(oc, never()).create_(any(Type.class), any(SOID.class), eq(fName2),
                any(PhysicalOp.class), any(Trans.class));
    }

    @Test
    public void shouldReplaceWithPreviouslyIgnoredFileIfOriginalFileIsDeletedFromFileSystem()
            throws Exception
    {
        // fName1 is part of logicRoot in AbstractTestMightCreate so fSOID can not be null
        SOID fSOID = ds.resolveNullable_(new Path(fName1));
        assign(fSOID, fFNT._fid);

        assertEquals(Result.FILE, mightCreate(fName1, fName1));
        assertEquals(Result.IGNORED, mightCreate(fName2, fName1));

        // Act as if fName1 is now deleted from file system by setting its FID to null.
        // Pretend it's the start of a new scan.
        reset(delBuffer);
        when(dr.getFIDAndType(Util.join(pRoot, fName1))).thenReturn(null);

        assertEquals(Result.FILE, mightCreate(fName2, fName2));
        verifyZeroInteractions(oc, om, hdmo);
        SOID fSOID2 = ds.resolveNullable_(new Path(fName2));
        verify(vu).update_(eq(new SOCKID(fSOID2, CID.CONTENT, KIndex.MASTER)), eq(t));
    }

    @Test
    public void shouldKeepOneButIgnoreOtherFoldersIfSOIDInDB() throws Exception
    {
        SOID dirSOID = ds.resolveNullable_(new Path(existingDirName));
        assign(dirSOID, dirFNT._fid);

        assertEquals(Result.IGNORED, mightCreate(nonExistingDirName, null));
        assertEquals(Result.EXISTING_FOLDER, mightCreate(existingDirName, existingDirName));
    }

    @Test
    public void shouldCreateOneFolderAndThenIgnoreOthersIfSOIDNotInDB() throws Exception
    {
        SOID dirSOID = ds.resolveNullable_(new Path(existingDirName));
        SOID soidRoot = new SOID(dirSOID.sidx(), OID.ROOT);

        assertEquals(Result.NEW_OR_REPLACED_FOLDER, mightCreate(nonExistingDirName, null));
        verify(oc).create_(Type.DIR, soidRoot, nonExistingDirName, PhysicalOp.MAP, t);

        // Assign the DirectoryService to return the new Path
        // as nonExistingDirName was created before existingDirName
        assign(dirSOID, dirFNT._fid);
        when(ds.resolveNullable_(dirSOID)).thenReturn(new Path(nonExistingDirName));

        assertEquals(Result.IGNORED, mightCreate(existingDirName, null));
        verify(oc, never()).create_(any(Type.class), any(SOID.class), eq(existingDirName),
                any(PhysicalOp.class), any(Trans.class));
    }

    @Test
    public void shouldReplaceWithPreviouslyIgnoredFolderIfOriginalFolderIsDeletedFromFileSystem()
            throws Exception
    {
        SOID dirSOID = ds.resolveNullable_(new Path(existingDirName));
        assign(dirSOID, dirFNT._fid);

        assertEquals(Result.IGNORED, mightCreate(nonExistingDirName, null));
        assertEquals(Result.EXISTING_FOLDER, mightCreate(existingDirName, existingDirName));

        // Act as if existingDirName is now deleted from file system by setting its FID to null.
        // Pretend it's the start of a new scan.
        reset(delBuffer);
        when(dr.getFIDAndType(Util.join(pRoot, existingDirName))).thenReturn(null);

        assertEquals(Result.EXISTING_FOLDER, mightCreate(nonExistingDirName, existingDirName));
        verify(hdmo).move_(eq(dirSOID), any(SOID.class), eq(nonExistingDirName), eq(PhysicalOp.MAP),
                eq(t));
    }
}
