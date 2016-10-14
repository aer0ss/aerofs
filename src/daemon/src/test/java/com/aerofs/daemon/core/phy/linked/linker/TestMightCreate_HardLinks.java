/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.core.phy.linked.linker.MightCreate.Result;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.*;
import com.aerofs.lib.Util;
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
        fFNT = dr.getFIDAndTypeNullable(Util.join(pRoot, fName1));
        when(dr.getFIDAndTypeNullable(Util.join(pRoot, fName2))).thenReturn(fFNT);
        when(dr.getFIDAndTypeNullable(Util.join(pRoot, fName3))).thenReturn(fFNT);

        // Simulate hard link between the directory names nonExistingDirName, existingDirName
        // by settting the same FID {@code dirFNT} to both of them
        dirFNT = dr.getFIDAndTypeNullable(Util.join(pRoot, existingDirName));
        when(dr.getFIDAndTypeNullable(Util.join(pRoot, nonExistingDirName))).thenReturn(dirFNT);
    }

    @Test
    public void shouldKeepOneButIgnoreOtherFilesIfSOIDInDB() throws Exception
    {
        // fName1 is part of logicRoot in AbstractTestMightCreate so fSOID can not be null
        SOID fSOID = ds.resolveNullable_(mkpath(fName1));
        assign(fSOID, fFNT._fid);

        assertEquals(Result.FILE, mightCreate(fName1));
        assertEquals(Result.IGNORED, mightCreate(fName2));
        assertEquals(Result.IGNORED, mightCreate(fName3));
    }

    @Test
    public void shouldCreateOneFileAndThenIgnoreOthersIfSOIDNotInDB() throws Exception
    {
        // fName1 is part of logicRoot in AbstractTestMightCreate so fSOID can not be null
        SOID fSOID = ds.resolveNullable_(mkpath(fName1));

        // Simulate fName1's creation in the following command.
        when(ds.resolveNullable_(mkpath(fName1))).thenReturn(null);

        assertEquals(Result.FILE, mightCreate(fName1));

        // Verify the file is created and set the DS to return the correct path
        // The DS now contains an entry for the (fSOID, fFNT._fid)
        // And the path is also stored now in the DS.
        verifyOperationExecuted(Operation.CREATE, null, null, fName1);

        assign(fSOID, fFNT._fid);
        when(ds.resolveNullable_(fSOID)).thenReturn(mkpath(fName1));

        assertEquals(Result.IGNORED, mightCreate(fName2));
        verifyNoMoreInteractions(mcop);
    }

    @Test
    public void shouldReplaceWithPreviouslyIgnoredFileIfOriginalFileIsDeletedFromFileSystem()
            throws Exception
    {
        // fName1 is part of logicRoot in AbstractTestMightCreate so fSOID can not be null
        SOID fSOID = ds.resolveNullable_(mkpath(fName1));
        SOID fSOID2 = ds.resolveNullable_(mkpath(fName2));
        assign(fSOID, fFNT._fid);

        assertEquals(Result.FILE, mightCreate(fName1));
        verifyOperationExecuted(Operation.UPDATE, fName1);
        assertEquals(Result.IGNORED, mightCreate(fName2));

        // Act as if fName1 is now deleted from file system by setting its FID to null.
        when(dr.getFIDAndTypeNullable(Util.join(pRoot, fName1))).thenReturn(null);

        assertEquals(Result.FILE, mightCreate(fName2));

        verifyOperationExecuted(Operation.REPLACE, fSOID, fSOID2, fName2);
    }

    @Test
    public void shouldKeepOneButIgnoreOtherFoldersIfSOIDInDB() throws Exception
    {
        SOID dirSOID = ds.resolveNullable_(mkpath(existingDirName));
        assign(dirSOID, dirFNT._fid);

        assertEquals(Result.IGNORED, mightCreate(nonExistingDirName));
        assertEquals(Result.EXISTING_FOLDER, mightCreate(existingDirName));
    }

    @Test
    public void shouldCreateOneFolderAndThenIgnoreOthersIfSOIDNotInDB() throws Exception
    {
        SOID dirSOID = ds.resolveNullable_(mkpath(existingDirName));

        assertEquals(Result.NEW_OR_REPLACED_FOLDER, mightCreate(nonExistingDirName));
        verifyOperationExecuted(Operation.CREATE, nonExistingDirName);

        // Assign the DirectoryService to return the mkpath
        // as nonExistingDirName was created before existingDirName
        assign(dirSOID, dirFNT._fid);
        when(ds.resolveNullable_(dirSOID)).thenReturn(mkpath(nonExistingDirName));

        assertEquals(Result.IGNORED, mightCreate(existingDirName));
        verifyNoMoreInteractions(mcop);
    }

    @Test
    public void shouldReplaceWithPreviouslyIgnoredFolderIfOriginalFolderIsDeletedFromFileSystem()
            throws Exception
    {
        SOID dirSOID = ds.resolveNullable_(mkpath(existingDirName));
        assign(dirSOID, dirFNT._fid);

        assertEquals(Result.IGNORED, mightCreate(nonExistingDirName));
        assertEquals(Result.EXISTING_FOLDER, mightCreate(existingDirName));
        verifyOperationExecuted(Operation.UPDATE, existingDirName);

        // Act as if existingDirName is now deleted from file system by setting its FID to null.
        when(dr.getFIDAndTypeNullable(Util.join(pRoot, existingDirName))).thenReturn(null);

        assertEquals(Result.EXISTING_FOLDER, mightCreate(nonExistingDirName));
        verifyOperationExecuted(Operation.UPDATE, dirSOID, null, nonExistingDirName);
    }
}
