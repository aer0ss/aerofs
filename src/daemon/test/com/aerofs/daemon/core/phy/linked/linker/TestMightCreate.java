/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.first_launch.OIDGenerator;
import com.aerofs.daemon.core.phy.linked.linker.MightCreate.Result;
import com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.Operation;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExFileNoPerm;
import com.aerofs.lib.ex.ExFileNotFound;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtilLinux;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestMightCreate extends AbstractMightCreateTest
{
    @Mock Trans t;
    @Mock IDeletionBuffer delBuffer;

    @Spy IOSUtil os = new OSUtilLinux();
    @Mock IgnoreList il;
    @Mock SharedFolderTagFileAndIcon sfti;
    @Mock MightCreateOperations mcop;

    @InjectMocks MightCreate mc;

    OIDGenerator og = new OIDGenerator(SID.generate(), "dummy");

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception
    {
        when(il.isIgnored("ignored")).thenReturn(true);

        when(mcop.executeOperation_(anySetOf(Operation.class), any(SOID.class), any(SOID.class),
                any(PathCombo.class), any(FIDAndType.class), eq(delBuffer), eq(og), eq(t)))
                .thenAnswer(new Answer<Boolean>() {
                    @Override
                    public Boolean answer(InvocationOnMock invocation) throws Throwable
                    {
                        return !Sets.intersection((Set<Operation>)invocation.getArguments()[0],
                                EnumSet.of(Operation.Create, Operation.Replace)).isEmpty();
                    }
                });

        // mock logical object hierarchy common to all tests
        mds = new MockDS(rootSID, ds);
        mds.root()
                .dir("d0").parent()
                .file("f1").parent()
                .anchor("a2").parent()
                .file("f3").parent()
                .dir("d4").parent()
                .dir("d-expelled", true)
                    .file("f", 0).parent().parent();
    }

    private Result mightCreate(String path, FIDAndType fnt) throws Exception
    {
        PathCombo pc = new PathCombo(absRootAnchor, mkpath(path));
        when(dr.getFIDAndType(eq(pc._absPath))).thenReturn(fnt);
        return mc.mightCreate_(pc, delBuffer, og, t);
    }

    private void verifyOperationExecuted(Operation op, SOID source, SOID target, String path)
            throws Exception
    {
        verifyOperationExecuted(EnumSet.of(op), source, target, path);
    }

    private void verifyOperationExecuted(Set<Operation> ops, SOID source, SOID target,
            String path) throws Exception
    {
        PathCombo pc = new PathCombo(absRootAnchor, mkpath(path));
        FIDAndType fnt = dr.getFIDAndType(pc._absPath);

        verify(mcop).executeOperation_(eq(ops), eq(source), eq(target), eq(pc), eq(fnt),
                eq(delBuffer), eq(og), eq(t));
    }

    @Test
    public void shouldIgnoreNameInIgnoreList() throws Exception
    {
        assertEquals(Result.IGNORED, mightCreate("ignored", generateFileFnt()));
    }

    @Test
    public void shouldIgnoreNullFID() throws Exception
    {
        assertEquals(Result.IGNORED, mightCreate("f1", null));
    }

    @Test
    public void shouldIgnoreFileWithNoParent() throws Exception
    {
        assertEquals(Result.IGNORED, mightCreate("foo/bar", generateFileFnt()));
    }

    @Test
    public void shouldIgnoreFileWithExpelledParent() throws Exception
    {
        assertEquals(Result.IGNORED, mightCreate("d-expelled/f", generateFileFnt()));
    }

    @Test
    public void shouldHonorFilter() throws Exception
    {
        OA oa = ds.getOA_(ds.resolveNullable_(mkpath("d0")));
        when(mcf.shouldIgnoreChilren_(any(PathCombo.class), eq(oa))).thenReturn(true);
        assertEquals(Result.IGNORED, mightCreate("d0/f", generateFileFnt()));
    }

    @Test
    public void shouldIgnoreFileIfGetFIDThrowsExFileNotFound() throws Exception
    {
        PathCombo pc = new PathCombo(absRootAnchor, mkpath("foo"));
        when(dr.getFIDAndType(eq(pc._absPath))).thenThrow(new ExFileNotFound(pc._path));

        assertEquals(Result.IGNORED, mc.mightCreate_(pc, delBuffer, og, t));
        verify(rocklog).newDefect("mc.fid.notfound");
    }

    @Test
    public void shouldIgnoreFileIfGetFIDThrowsExFileNoPerm() throws Exception
    {
        PathCombo pc = new PathCombo(absRootAnchor, mkpath("foo"));
        when(dr.getFIDAndType(eq(pc._absPath))).thenThrow(new ExFileNoPerm(new File(pc._absPath)));

        assertEquals(Result.IGNORED, mc.mightCreate_(pc, delBuffer, og, t));
        verify(rocklog).newDefect("mc.fid.noperm");
    }

    @Test
    public void shouldIgnoreFileIfGetFIDThrowsException() throws Exception
    {
        PathCombo pc = new PathCombo(absRootAnchor, mkpath("foo"));
        when(dr.getFIDAndType(eq(pc._absPath))).thenThrow(new IOException("foo"));

        assertEquals(Result.IGNORED, mc.mightCreate_(pc, delBuffer, og, t));
        verify(rocklog).newDefect("mc.fid.exception");
    }

    @Test
    public void shouldUpdateExistingFile() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("f1"));
        FIDAndType fnt = generateFileFnt(soid);

        assertEquals(Result.FILE, mightCreate("f1", fnt));

        verifyOperationExecuted(Operation.Update, soid, soid, "f1");
    }

    @Test
    public void shouldUpdateExistingFolder() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("d0"));
        FIDAndType fnt = generateDirFnt(soid);

        assertEquals(Result.EXISTING_FOLDER, mightCreate("d0", fnt));

        verifyOperationExecuted(Operation.Update, soid, soid, "d0");
    }

    @Test
    public void shouldUpdateExistingFileOnMove() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("f1"));
        FIDAndType fnt = generateFileFnt(soid);

        assertEquals(Result.FILE, mightCreate("f1-moved", fnt));

        verifyOperationExecuted(Operation.Update, soid, null, "f1-moved");
    }

    @Test
    public void shouldUpdateExistingFolderOnMove() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("d0"));
        FIDAndType fnt = generateDirFnt(soid);

        assertEquals(Result.EXISTING_FOLDER, mightCreate("d0-moved", fnt));

        verifyOperationExecuted(Operation.Update, soid, null, "d0-moved");
    }

    @Test
    public void shouldCreateNewFile() throws Exception
    {
        FIDAndType fnt = generateFileFnt();

        assertEquals(Result.FILE, mightCreate("f0", fnt));

        verifyOperationExecuted(Operation.Create, null, null, "f0");
    }

    @Test
    public void shouldCreateNewFolder() throws Exception
    {
        FIDAndType fnt = generateDirFnt();

        assertEquals(Result.NEW_OR_REPLACED_FOLDER, mightCreate("d1", fnt));

        verifyOperationExecuted(Operation.Create, null, null, "d1");
    }

    @Test
    public void shouldCreateNewFileCaseSensitive() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("f1"));
        generateFileFnt(soid);
        FIDAndType fnt = generateFileFnt();

        assertEquals(Result.FILE, mightCreate("F1", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Create),
                null, null, "F1");
    }

    @Test
    public void shouldCreateNewFolderCaseSensitive() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("d0"));
        generateDirFnt(soid);
        FIDAndType fnt = generateDirFnt();

        assertEquals(Result.NEW_OR_REPLACED_FOLDER, mightCreate("D0", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Create),
                null, null, "D0");
    }

    @Test
    public void shouldReplaceExistingFile() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("f1"));
        generateFileFnt(soid);
        FIDAndType fnt = generateFileFnt();

        assertEquals(Result.FILE, mightCreate("f1", fnt));

        verifyOperationExecuted(Operation.Replace, null, soid, "f1");
    }

    @Test
    public void shouldReplaceExistingFileWhenSourceExist() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("f1"));
        SOID src = ds.resolveNullable_(mkpath("f3"));
        generateFileFnt(soid);
        FIDAndType fnt2 = generateFileFnt(src);

        assertEquals(Result.FILE, mightCreate("f1", fnt2));

        verifyOperationExecuted(EnumSet.of(Operation.Replace), src, soid, "f1");
    }

    @Test
    public void shouldReplaceExistingFolder() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("d0"));
        generateDirFnt(soid);
        FIDAndType fnt = generateDirFnt();

        assertEquals(Result.NEW_OR_REPLACED_FOLDER, mightCreate("d0", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Replace),
                null, soid, "d0");
    }

    @Test
    public void shouldUpdateSourceFileAndRenameTargetDir() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("d0"));
        SOID src = ds.resolveNullable_(mkpath("f1"));
        generateDirFnt(soid);
        FIDAndType fnt = generateFileFnt(src);

        assertEquals(Result.FILE, mightCreate("d0", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Update, Operation.RenameTarget),
                src, soid, "d0");
    }

    @Test
    public void shouldUpdateSourceDirAndRenameTargetDir() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("d0"));
        SOID src = ds.resolveNullable_(mkpath("d4"));
        generateDirFnt(soid);
        FIDAndType fnt = generateDirFnt(src);

        assertEquals(Result.EXISTING_FOLDER, mightCreate("d0", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Update, Operation.RenameTarget),
                src, soid, "d0");
    }

    @Test
    public void shouldRenameExistingAnchorAndCreateNewFolder() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("a2"));
        generateDirFnt(soid);
        FIDAndType fnt = generateDirFnt();

        assertEquals(Result.NEW_OR_REPLACED_FOLDER, mightCreate("a2", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Create, Operation.RenameTarget),
                null, soid, "a2");
    }

    @Test
    public void shouldRepaceFIDForAnchorWithMatchingTag() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("a2"));
        generateDirFnt(soid);
        FIDAndType fnt = generateDirFnt();
        when(sfti.isSharedFolderRoot(any(SID.class), eq(Util.join(absRootAnchor, "a2"))))
                .thenReturn(true);

        assertEquals(Result.NEW_OR_REPLACED_FOLDER, mightCreate("a2", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Replace),
                null, soid, "a2");
    }

    @Test
    public void shouldCreateNewFolderAndRandomizeSourceFID() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("f1"));
        FIDAndType fnt = generateDirFnt(soid);

        assertEquals(Result.NEW_OR_REPLACED_FOLDER, mightCreate("f2", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Create, Operation.RandomizeSourceFID),
                soid, null, "f2");
    }

    @Test
    public void shouldCreateNewFileAndRandomizeSourceFID() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("d0"));
        FIDAndType fnt = generateFileFnt(soid);

        assertEquals(Result.FILE, mightCreate("d2", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Create, Operation.RandomizeSourceFID),
                soid, null, "d2");
    }

    @Test
    public void shouldCreateNewFolderAndRenameTargetFile() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("f1"));
        generateFileFnt(soid);
        FIDAndType fnt = generateDirFnt();

        assertEquals(Result.NEW_OR_REPLACED_FOLDER, mightCreate("f1", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Create, Operation.RenameTarget),
                null, soid, "f1");
    }

    @Test
    public void shouldCreateNewFileAndRenameTargetFolder() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("d0"));
        generateDirFnt(soid);
        FIDAndType fnt = generateFileFnt();

        assertEquals(Result.FILE, mightCreate("d0", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Create, Operation.RenameTarget),
                null, soid, "d0");
    }

    @Test
    public void shouldCreateNewFolderAndRenameExpelledTarget() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("d-expelled"));
        generateDirFnt(soid);
        FIDAndType fnt = generateDirFnt();

        assertEquals(Result.NEW_OR_REPLACED_FOLDER, mightCreate("d-expelled", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Create, Operation.RenameTarget),
                null, soid, "d-expelled");
    }

    @Test
    public void shouldUpdateExistingFolderAndRenameTargetFile() throws Exception
    {
        SOID target = ds.resolveNullable_(mkpath("f1"));
        generateFileFnt(target);

        SOID source = ds.resolveNullable_(mkpath("d0"));
        FIDAndType fnt = generateDirFnt(source);

        assertEquals(Result.EXISTING_FOLDER, mightCreate("f1", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Update, Operation.RenameTarget),
                source, target, "f1");
    }

    @Test
    public void shouldUpdateExistingFileAndRenameTargetFolder() throws Exception
    {
        SOID target = ds.resolveNullable_(mkpath("d0"));
        generateDirFnt(target);

        SOID source = ds.resolveNullable_(mkpath("f1"));
        FIDAndType fnt = generateFileFnt(source);

        assertEquals(Result.FILE, mightCreate("d0", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Update, Operation.RenameTarget),
                source, target, "d0");
    }

    @Test
    public void shouldCreateRandomizeFIDAndRenameTarget() throws Exception
    {
        SOID target = ds.resolveNullable_(mkpath("d0"));
        generateDirFnt(target);

        SOID source = ds.resolveNullable_(mkpath("d4"));
        FIDAndType fnt = generateFileFnt(source);

        assertEquals(Result.FILE, mightCreate("d0", fnt));

        verifyOperationExecuted(
                EnumSet.of(Operation.Create, Operation.RandomizeSourceFID, Operation.RenameTarget),
                source, target, "d0");
    }

    @Test
    public void shouldCreateRandomizeFIDAndRenameNROTarget() throws Exception
    {
        SOID target = ds.resolveNullable_(mkpath("f1"));
        generateFileFnt(target);

        when(rh.isNonRepresentable(oaAt("f1"))).thenReturn(true);

        SOID source = ds.resolveNullable_(mkpath("d4"));
        FIDAndType fnt = generateFileFnt(source);

        assertEquals(Result.FILE, mightCreate("f1", fnt));

        verifyOperationExecuted(
                EnumSet.of(Operation.Create, Operation.RandomizeSourceFID, Operation.RenameTarget, Operation.NonRepresentableTarget),
                source, target, "f1");
    }

    @Test
    public void shouldUpdateExistingFolderAndRenameExpelledTarget() throws Exception
    {
        SOID target = ds.resolveNullable_(mkpath("d-expelled"));
        generateDirFnt(target);

        SOID source = ds.resolveNullable_(mkpath("d0"));
        FIDAndType fnt = generateDirFnt(source);

        assertEquals(Result.EXISTING_FOLDER, mightCreate("d-expelled", fnt));

        verifyOperationExecuted(EnumSet.of(Operation.Update, Operation.RenameTarget),
                source, target, "d-expelled");
    }

    @Test
    public void shouldCreateNewFileAndRenameNonRepresentableTarget() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("f1"));
        generateFileFnt(soid);
        FIDAndType fnt = generateFileFnt();

        when(rh.isNonRepresentable(oaAt("f1"))).thenReturn(true);

        assertEquals(Result.FILE, mightCreate("f1", fnt));

        verifyOperationExecuted(
                EnumSet.of(Operation.Create, Operation.RenameTarget,
                        Operation.NonRepresentableTarget),
                null, soid, "f1");
    }

    @Test
    public void shouldCreateNewFolderAndRenameNonRepresentableTarget() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("d0"));
        generateDirFnt(soid);
        FIDAndType fnt = generateDirFnt();

        when(rh.isNonRepresentable(oaAt("d0"))).thenReturn(true);

        assertEquals(Result.NEW_OR_REPLACED_FOLDER, mightCreate("d0", fnt));

        verifyOperationExecuted(
                EnumSet.of(Operation.Create, Operation.RenameTarget,
                        Operation.NonRepresentableTarget),
                null, soid, "d0");
    }

    @Test
    public void shouldIgnoreHardlink() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("f1"));
        FIDAndType fnt = generateFileFnt(soid);

        when(dr.getFIDAndType(Util.join(lrm.absRootAnchor_(rootSID), "f1"))).thenReturn(fnt);

        assertEquals(Result.IGNORED, mightCreate("f42", fnt));
    }

    @Test
    public void shouldIgnoreHardlinkCaseSensitive() throws Exception
    {
        SOID soid = ds.resolveNullable_(mkpath("f1"));
        FIDAndType fnt = generateFileFnt(soid);

        when(dr.getFIDAndType(Util.join(lrm.absRootAnchor_(rootSID), "f1"))).thenReturn(fnt);

        assertEquals(Result.IGNORED, mightCreate("F1", fnt));
    }

    @Test
    public void shouldRenameCaseInsensitive() throws Exception
    {
        caseInsensitive();
        SOID soid = ds.resolveNullable_(mkpath("f1"));
        FIDAndType fnt = generateFileFnt(soid);

        when(dr.getFIDAndType(Util.join(lrm.absRootAnchor_(rootSID), "f1"))).thenReturn(fnt);

        assertEquals(Result.FILE, mightCreate("F1", fnt));
        verifyOperationExecuted(EnumSet.of(Operation.Update),
                soid, null, "F1");
    }
}
