/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.first.OIDGenerator;
import com.aerofs.daemon.core.mock.logical.LogicalObjectsPrinter;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.injectable.InjectableFile;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static com.aerofs.daemon.core.ds.OA.Type.*;
import static com.aerofs.daemon.core.phy.PhysicalOp.*;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.*;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.Operation.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;

import java.util.Arrays;
import java.util.EnumSet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TestMightCreateOperations extends AbstractMightCreateTest
{
    @Mock ObjectMover om;
    @Mock ObjectCreator oc;
    @Mock VersionUpdater vu;
    @Mock InjectableFile.Factory factFile;
    @Mock SharedFolderTagFileAndIcon sfti;
    @Mock Analytics analytics;
    @Mock IDeletionBuffer delBuffer;

    @InjectMocks MightCreateOperations mcop;

    OIDGenerator og = new OIDGenerator("dummy");

    @Before
    public void setUp() throws Exception
    {
        // setup a basic object tree for tests
        mds = new MockDS(rootSID, ds);
        mds.root()
                .dir("foo")
                        .dir("bar")
                                .file("hello").caMaster(42, 0).parent()
                                .file("world").caMaster(42, 0).parent().parent().parent()
                .dir("baz");

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // need to react to object moves
        when(om.move_(any(SOID.class), any(SOID.class), anyString(), eq(MAP), eq(t)))
                .thenAnswer( new Answer<SOID>() {
                    @Override
                    public SOID answer(InvocationOnMock invocation) throws Throwable
                    {
                        Object[] args = invocation.getArguments();
                        Path from = ds.resolve_((SOID)args[0]);
                        Path toParent = ds.resolve_((SOID)args[1]);
                        String name = (String)args[2];
                        mds.move(from.toStringRelative(), toParent.append(name).toStringRelative(), t);
                        return (SOID)args[0];
                    }
                }
        );
    }

    void fileModified(Path path) throws Exception
    {
        fileModified(path, true);
    }

    void fileModified(Path path, boolean modified) throws Exception
    {
        fileModified(path, path.toStringRelative(), modified);
    }

    void fileModified(Path logical, String physical, boolean modified) throws Exception
    {
        SOID soid = ds.resolveNullable_(logical);

        InjectableFile f = mock(InjectableFile.class);
        CA ca = ds.getOA_(soid).caMaster();
        when(f.wasModifiedSince(ca.mtime(), ca.length())).thenReturn(modified);
        when(factFile.create(Util.join(absRootAnchor, physical))).thenReturn(f);

        l.info("modif {} {}", logical, Util.join(absRootAnchor, physical));
    }

    private void op(String path, FIDAndType fnt, Operation op, Operation... flags) throws Exception
    {
        PathCombo pc = new PathCombo(absRootAnchor, mkpath(path));
        SOID src = ds.getSOIDNullable_(fnt._fid);
        SOID dst = ds.resolveNullable_(pc._path);
        l.info("mcop {} {}", src, dst);
        mcop.executeOperation_(EnumSet.of(op, flags), src, dst, pc, fnt, delBuffer, og, t);
    }

    private InjectableFile mockPhy(boolean dir, String... pathElems)
    {
        String parentPath = Util.join(Arrays.copyOf(pathElems, pathElems.length - 1));
        String absPath = Util.join(pathElems);
        InjectableFile f = mock(InjectableFile.class);
        when(f.isDirectory()).thenReturn(dir);
        when(f.isFile()).thenReturn(!dir);
        when(f.exists()).thenReturn(true);
        when(f.getAbsolutePath()).thenReturn(absPath);
        when(f.getParent()).thenReturn(parentPath);
        when(factFile.create(absPath)).thenReturn(f);
        when(factFile.create(parentPath, pathElems[pathElems.length - 1])).thenReturn(f);
        return f;
    }

    private InjectableFile mockPhyDir(String... pathElems)
    {
        return mockPhy(true, pathElems);
    }

    private InjectableFile mockPhyFile(InjectableFile parent, String name)
    {
        InjectableFile f =  mockPhy(false, parent.getAbsolutePath(), name);
        when(factFile.create(parent, name)).thenReturn(f);
        return f;
    }

    @Test
    public void shouldCreateNewFile() throws Exception
    {
        FIDAndType fnt = generateFileFnt();
        op("baz/new", fnt, Create);

        verify(oc).create_(eq(FILE), any(OID.class), soidAt("baz"), eq("new"), eq(MAP), eq(t));
        verifyZeroInteractions(vu, om, delBuffer);
    }

    @Test
    public void shouldCreateNewDir() throws Exception
    {
        FIDAndType fnt = generateDirFnt();
        op("baz/new", fnt, Create);

        verify(oc).create_(eq(DIR), any(OID.class), soidAt("baz"), eq("new"), eq(MAP), eq(t));
        verifyZeroInteractions(vu, om, delBuffer);
    }

    @Test
    public void shouldUpdateFile() throws Exception
    {
        Path path = mkpath("foo/bar/hello");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateFileFnt(soid);

        fileModified(path);

        op("foo/bar/hello", fnt, Update);

        verify(vu).update_(new SOCKID(soid, CID.CONTENT), t);
        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc, om);
    }

    @Test
    public void shouldUpdateDir() throws Exception
    {
        Path path = mkpath("foo/bar");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateDirFnt(soid);

        op("foo/bar", fnt, Update);

        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc, om, vu);
    }

    @Test
    public void shouldRenameFile() throws Exception
    {
        Path path = mkpath("foo/bar/hello");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateFileFnt(soid);

        fileModified(path, "foo/hello2", false);

        op("foo/hello2", fnt, Update);

        verify(om).move_(eq(soid), soidAt("foo"), eq("hello2"), eq(MAP), eq(t));
        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc, vu);
    }

    @Test
    public void shouldRenameFileAndUpdateContent() throws Exception
    {
        Path path = mkpath("foo/bar/hello");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateFileFnt(soid);

        fileModified(path, "foo/hello2", true);

        op("foo/hello2", fnt, Update);

        verify(om).move_(eq(soid), soidAt("foo"), eq("hello2"), eq(MAP), eq(t));
        verify(vu).update_(new SOCKID(soid, CID.CONTENT), t);
        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc);
    }

    @Test
    public void shouldRenameDir() throws Exception
    {
        Path path = mkpath("baz");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateDirFnt(soid);

        op("qux", fnt, Update);

        verify(om).move_(eq(soid), soidAt(""), eq("qux"), eq(MAP), eq(t));
        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc, vu);
    }

    @Test
    public void shouldReplaceFile() throws Exception
    {
        Path path = mkpath("foo/bar/hello");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateFileFnt();

        fileModified(path);

        op("foo/bar/hello", fnt, Replace);

        verify(ds).setFID_(soid, fnt._fid, t);
        verify(vu).update_(new SOCKID(soid, CID.CONTENT), t);
        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc, om);
    }

    @Test
    public void shouldReplaceDir() throws Exception
    {
        Path path = mkpath("foo/bar");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateDirFnt();

        op("foo/bar", fnt, Replace);

        verify(ds).setFID_(soid, fnt._fid, t);
        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc, om, vu);
    }

    @Test
    public void shouldRenameTarget() throws Exception
    {
        FIDAndType fnt = generateFileFnt();

        InjectableFile parent = mockPhyDir(absRootAnchor, "foo");
        mockPhyFile(parent, "bar");
        mockPhyFile(parent, "bar (2)");
        when(mockPhyFile(parent, "bar (3)").exists()).thenReturn(false);

        op("foo/bar", fnt, Create, RenameTarget);

        verify(om).moveInSameStore_(soidAt("foo/bar"), oidAt("foo"), eq("bar (3)"), eq(MAP),
                eq(false), eq(true), eq(t));
        verify(oc).create_(eq(FILE), any(OID.class), soidAt("foo"), eq("bar"), eq(MAP), eq(t));
        verifyZeroInteractions(delBuffer, vu);
    }

    @Test
    public void shouldRenameTargetAndRandomizeTargetFID() throws Exception
    {
        Path path = mkpath("foo/bar");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateFileFnt(soid);

        InjectableFile parent = mockPhyDir(absRootAnchor, "foo");
        mockPhyFile(parent, "bar");
        when(mockPhyFile(parent, "bar (2)").exists()).thenReturn(false);

        op("foo/bar", fnt, Create, RenameTarget);

        verify(ds).setFID_(eq(soid), any(FID.class), eq(t));
        verify(om).moveInSameStore_(soidAt("foo/bar"), oidAt("foo"), eq("bar (2)"), eq(MAP),
                eq(false), eq(true), eq(t));
        verify(oc).create_(eq(FILE), any(OID.class), soidAt("foo"), eq("bar"), eq(MAP), eq(t));
        verifyZeroInteractions(delBuffer, vu);
    }

    @Test
    public void shouldRandomizeSourceFID() throws Exception
    {
        Path path = mkpath("foo/bar");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateFileFnt(soid);

        op("new", fnt, Create, RandomizeSourceFID);

        verify(ds).setFID_(eq(soid), any(FID.class), eq(t));
        verify(oc).create_(eq(FILE), any(OID.class), soidAt(""), eq("new"), eq(MAP), eq(t));
        verifyZeroInteractions(delBuffer, om, vu);
    }

    @Test
    public void shouldReplaceFolderAndCleanupSource() throws Exception
    {
        Path path = mkpath("foo");
        SOID source = ds.resolveNullable_(path);
        FIDAndType fnt = generateDirFnt(source);

        SOID target = ds.resolveNullable_(mkpath("baz"));

        op("baz", fnt, Replace);

        verify(ds).setFID_(eq(source), any(FID.class), eq(t));
        verify(ds).setFID_(eq(target), eq(fnt._fid), eq(t));
        verifyZeroInteractions(vu, om, oc);
    }

    @Test
    public void shouldReplaceFileAndCleanupSource() throws Exception
    {
        Path path = mkpath("foo/bar/hello");
        SOID source = ds.resolveNullable_(path);
        FIDAndType fnt = generateFileFnt(source);

        SOID target = ds.resolveNullable_(mkpath("foo/bar/world"));

        op("foo/bar/world", fnt, Replace);

        verify(ds).setFID_(eq(source), any(FID.class), eq(t));
        verify(ds).setCA_(new SOKID(source, KIndex.MASTER), -1L, 0L, null, t);
        verify(ds).setFID_(eq(target), eq(fnt._fid), eq(t));
        verify(vu).update_(new SOCKID(target, CID.CONTENT), t);
        verifyZeroInteractions(vu, om, oc);
    }
}
