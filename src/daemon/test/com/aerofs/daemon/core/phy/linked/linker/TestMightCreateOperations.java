/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.first_launch.OIDGenerator;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.mock.logical.LogicalObjectsPrinter;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.injectable.InjectableFile;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.EnumSet;

import static com.aerofs.daemon.core.ds.OA.Type.DIR;
import static com.aerofs.daemon.core.ds.OA.Type.FILE;
import static com.aerofs.daemon.core.phy.PhysicalOp.MAP;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.Operation;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.Operation.CREATE;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.Operation.RANDOMIZE_SOURCE_FID;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.Operation.RENAME_TARGET;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.Operation.REPLACE;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.Operation.UPDATE;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TestMightCreateOperations extends AbstractMightCreateTest
{
    @Mock ObjectMover om;
    @Mock ImmigrantCreator imc;
    @Mock ObjectCreator oc;
    @Mock InjectableFile.Factory factFile;
    @Mock SharedFolderTagFileAndIcon sfti;
    @Mock Analytics analytics;
    @Mock IDeletionBuffer delBuffer;
    @Mock SIDMap sm;
    @Mock CoreScheduler sched;
    @Mock HashQueue hq;

    @InjectMocks MightCreateOperations mcop;

    OIDGenerator og = new OIDGenerator(SID.generate(), "dummy");

    @Before
    public void setUp() throws Exception
    {
        // setup a basic object tree for tests
        mds = new MockDS(rootSID, ds, sm, sm);
        mds.root()
                .dir("foo")
                        .dir("bar")
                                .file("hello").caMaster(42, 0).parent()
                                .file("world").caMaster(42, 0).parent().parent().parent()
                .dir("baz").parent()
                .anchor("shared");

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // need to react to object moves
        when(imc.move_(any(SOID.class), any(SOID.class), anyString(), eq(MAP), eq(t)))
                .thenAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    Path from = ds.resolve_((SOID)args[0]);
                    Path toParent = ds.resolve_((SOID)args[1]);
                    String name = (String)args[2];
                    mds.move(from.toStringRelative(), toParent.append(name).toStringRelative(), t);
                    return (SOID)args[0];
                });

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            AbstractEBSelfHandling e = (AbstractEBSelfHandling)args[0];
            e.handle_();
            return null;
        }).when(sched).schedule(any(IEvent.class), anyLong());
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

        InjectableFile parent = mockPhyDir(absRootAnchor, "baz");
        mockPhyFile(parent, "new");

        op("baz/new", fnt, CREATE);

        verify(oc).createMetaForLinker_(eq(FILE), any(OID.class), soidAt("baz"), eq("new"), eq(t));
        verify(hq).requestHash_(any(SOID.class), any(InjectableFile.class), anyLong(), anyLong(),
                eq(t));
        verifyZeroInteractions(hq, om, delBuffer, sfti);
    }

    @Test
    public void shouldCreateNewDir() throws Exception
    {
        FIDAndType fnt = generateDirFnt();
        op("baz/new", fnt, CREATE);

        verify(oc).createMetaForLinker_(eq(DIR), any(OID.class), soidAt("baz"), eq("new"), eq(t));
        verify(sfti).getOIDForAnchor_(any(SIndex.class), any(PathCombo.class), eq(t));
        verifyZeroInteractions(hq, om, delBuffer);
    }

    @Test
    public void shouldUpdateFile() throws Exception
    {
        Path path = mkpath("foo/bar/hello");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateFileFnt(soid);

        fileModified(path);

        op("foo/bar/hello", fnt, UPDATE);

        verify(hq).requestHash_(eq(soid), any(InjectableFile.class), anyLong(), anyLong(), eq(t));
        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc, om, sfti);
    }

    @Test
    public void shouldUpdateDir() throws Exception
    {
        Path path = mkpath("foo/bar");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateDirFnt(soid);

        op("foo/bar", fnt, UPDATE);

        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc, om, hq, sfti);
    }

    @Test
    public void shouldRenameFile() throws Exception
    {
        Path path = mkpath("foo/bar/hello");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateFileFnt(soid);

        // hash computed when missing, even if size and mtime match
        when(ds.getCAHash_(new SOKID(soid, KIndex.MASTER)))
                .thenReturn(new ContentHash(new byte[ContentHash.LENGTH]));

        fileModified(path, "foo/hello2", false);

        op("foo/hello2", fnt, UPDATE);

        verify(imc).move_(eq(soid), soidAt("foo"), eq("hello2"), eq(MAP), eq(t));
        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc, hq, sfti);
    }

    @Test
    public void shouldRenameFileAndUpdateContent() throws Exception
    {
        Path path = mkpath("foo/bar/hello");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateFileFnt(soid);

        fileModified(path, "foo/hello2", true);

        op("foo/hello2", fnt, UPDATE);

        verify(imc).move_(eq(soid), soidAt("foo"), eq("hello2"), eq(MAP), eq(t));
        verify(hq).requestHash_(eq(soid), any(InjectableFile.class), anyLong(), anyLong(), eq(t));
        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc, sfti);
    }

    @Test
    public void shouldRenameDir() throws Exception
    {
        Path path = mkpath("baz");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateDirFnt(soid);

        op("qux", fnt, UPDATE);

        verify(imc).move_(eq(soid), soidAt(""), eq("qux"), eq(MAP), eq(t));
        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc, hq, sfti);
    }

    @Test
    public void shouldReplaceFile() throws Exception
    {
        Path path = mkpath("foo/bar/hello");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateFileFnt();

        fileModified(path);

        op("foo/bar/hello", fnt, REPLACE);

        verify(ds).setFID_(soid, fnt._fid, t);
        verify(hq).requestHash_(eq(soid), any(InjectableFile.class), anyLong(), anyLong(), eq(t));
        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc, om, sfti);
    }

    @Test
    public void shouldReplaceDir() throws Exception
    {
        Path path = mkpath("foo/bar");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateDirFnt();

        op("foo/bar", fnt, REPLACE);

        verify(ds).setFID_(soid, fnt._fid, t);
        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc, om, hq, sfti);
    }

    @Test
    public void shouldRenameTarget() throws Exception
    {
        FIDAndType fnt = generateFileFnt();

        InjectableFile parent = mockPhyDir(absRootAnchor, "foo");
        mockPhyFile(parent, "bar");
        mockPhyFile(parent, "bar (2)");
        when(mockPhyFile(parent, "bar (3)").exists()).thenReturn(false);

        op("foo/bar", fnt, CREATE, RENAME_TARGET);

        verify(om).moveInSameStore_(soidAt("foo/bar"), oidAt("foo"), eq("bar (3)"), eq(MAP),
                eq(true), eq(t));
        verify(oc).createMetaForLinker_(eq(FILE), any(OID.class), soidAt("foo"), eq("bar"), eq(t));
        verify(hq).requestHash_(any(SOID.class), any(InjectableFile.class), anyLong(), anyLong(),
                eq(t));
        verifyZeroInteractions(delBuffer, hq, sfti);
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

        op("foo/bar", fnt, CREATE, RENAME_TARGET);

        verify(ds).setFID_(eq(soid), any(FID.class), eq(t));
        verify(om).moveInSameStore_(soidAt("foo/bar"), oidAt("foo"), eq("bar (2)"), eq(MAP),
                eq(true), eq(t));
        verify(oc).createMetaForLinker_(eq(FILE), any(OID.class), soidAt("foo"), eq("bar"), eq(t));
        verify(hq).requestHash_(any(SOID.class), any(InjectableFile.class), anyLong(), anyLong(),
                eq(t));
        verifyZeroInteractions(delBuffer, hq, sfti);
    }

    @Test
    public void shouldRandomizeSourceFID() throws Exception
    {
        Path path = mkpath("foo/bar");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateFileFnt(soid);

        InjectableFile parent = mockPhyDir(absRootAnchor);
        mockPhyFile(parent, "new");

        op("new", fnt, CREATE, RANDOMIZE_SOURCE_FID);

        verify(ds).setFID_(eq(soid), any(FID.class), eq(t));
        verify(oc).createMetaForLinker_(eq(FILE), any(OID.class), soidAt(""), eq("new"), eq(t));
        verify(hq).requestHash_(any(SOID.class), any(InjectableFile.class), anyLong(), anyLong(),
                eq(t));
        verifyZeroInteractions(delBuffer, om, hq, sfti);
    }

    @Test
    public void shouldReplaceFolderAndCleanupSource() throws Exception
    {
        Path path = mkpath("foo");
        SOID source = ds.resolveNullable_(path);
        FIDAndType fnt = generateDirFnt(source);

        SOID target = ds.resolveNullable_(mkpath("baz"));

        op("baz", fnt, REPLACE);

        verify(ds).setFID_(eq(source), any(FID.class), eq(t));
        verify(ds).setFID_(eq(target), eq(fnt._fid), eq(t));
        verifyZeroInteractions(hq, om, oc, sfti);
    }

    @Test
    public void shouldReplaceFileAndCleanupSource() throws Exception
    {
        Path path = mkpath("foo/bar/hello");
        SOID source = ds.resolveNullable_(path);
        FIDAndType fnt = generateFileFnt(source);

        SOID target = ds.resolveNullable_(mkpath("foo/bar/world"));

        mockPhyFile(mockPhyDir(absRootAnchor, "foo", "bar"), "world");
        op("foo/bar/world", fnt, REPLACE);

        verify(ds).setFID_(eq(source), any(FID.class), eq(t));
        verify(ds).setCA_(new SOKID(source, KIndex.MASTER), -1L, 0L, null, t);
        verify(ds).setFID_(eq(target), eq(fnt._fid), eq(t));
        verify(hq).requestHash_(eq(target), any(InjectableFile.class), anyLong(), anyLong(), eq(t));
        verifyZeroInteractions(hq, om, oc, sfti);
    }

    @Test
    public void shouldFixTagFileWhenUpdatingAnchor() throws Exception
    {
        Path path = mkpath("shared");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateDirFnt(soid);

        String absPath = Util.join(absRootAnchor, "shared");
        SID sid = SID.anchorOID2storeSID(soid.oid());

        when(sfti.isSharedFolderRoot(sid, absPath)).thenReturn(false);

        op("shared", fnt, UPDATE);

        verify(delBuffer).remove_(soid);
        verify(sched).schedule(any(IEvent.class), anyLong());
        verify(sfti).isSharedFolderRoot(sid, absPath);
        verify(sfti).fixTagFileIfNeeded_(sid, absPath);
        verifyZeroInteractions(oc, om, hq);
    }

    @Test
    public void shouldFixTagFileWhenRenamingAnchor() throws Exception
    {
        Path path = mkpath("shared");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateDirFnt(soid);

        String absPath = Util.join(absRootAnchor, "quux");
        SID sid = SID.anchorOID2storeSID(soid.oid());

        when(sfti.isSharedFolderRoot(sid, absPath)).thenReturn(false);

        op("quux", fnt, UPDATE);

        verify(imc).move_(eq(soid), soidAt(""), eq("quux"), eq(MAP), eq(t));
        verify(delBuffer).remove_(soid);
        verify(sched).schedule(any(IEvent.class), anyLong());
        verify(sfti).isSharedFolderRoot(sid, absPath);
        verify(sfti).fixTagFileIfNeeded_(sid, absPath);
        verifyZeroInteractions(oc, om, hq);
    }

    @Test
    public void shouldFixTagFileWhenReplacingAnchor() throws Exception
    {
        Path path = mkpath("shared");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateDirFnt();

        String absPath = Util.join(absRootAnchor, "shared");
        SID sid = SID.anchorOID2storeSID(soid.oid());

        when(sfti.isSharedFolderRoot(sid, absPath)).thenReturn(false);

        op("shared", fnt, REPLACE);

        verify(ds).setFID_(soid, fnt._fid, t);
        verify(delBuffer).remove_(soid);
        verify(sched).schedule(any(IEvent.class), anyLong());
        verify(sfti).isSharedFolderRoot(sid, absPath);
        verify(sfti).fixTagFileIfNeeded_(sid, absPath);
        verifyZeroInteractions(oc, om, hq);
    }
}
