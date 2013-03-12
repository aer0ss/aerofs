/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.linker;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.first.OIDGenerator;
import com.aerofs.daemon.core.mock.logical.IsSOIDAtPath;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.analytics.Analytics;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.annotation.Nullable;

import static com.aerofs.daemon.core.ds.OA.Type.*;
import static com.aerofs.daemon.core.phy.PhysicalOp.*;
import static com.aerofs.daemon.core.linker.MightCreateOperations.*;
import static com.aerofs.daemon.core.linker.MightCreateOperations.Operation.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;

import java.sql.SQLException;
import java.util.EnumSet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class MightCreateOperationsTest extends AbstractTest
{
    @Mock DirectoryService ds;
    @Mock OIDGenerator og;
    @Mock ObjectMover om;
    @Mock ObjectCreator oc;
    @Mock InjectableDriver dr;
    @Mock VersionUpdater vu;
    @Mock InjectableFile.Factory factFile;
    @Mock SharedFolderTagFileAndIcon sfti;
    @Mock Analytics analytics;
    @Mock IDeletionBuffer delBuffer;
    @Mock Trans t;
    @Mock CfgAbsRootAnchor cfgAbsRootanchor;

    @InjectMocks MightCreateOperations mcop;

    @Before
    public void setUp() throws Exception
    {
        when(cfgAbsRootanchor.get()).thenReturn("/AeroFS");

        // 64bit fid: 8 bytes
        when(dr.getFIDLength()).thenReturn(8);

        // setup a basic object tree for tests
        final MockDS mds = new MockDS(ds);
        mds.root()
                .dir("foo")
                        .dir("bar")
                                .file("hello").caMaster(42, 0).parent()
                                .file("world").caMaster(42, 0).parent().parent().parent()
                .dir("baz");

        when(og.generate_(anyBoolean(), any(Path.class))).thenAnswer(new Answer<OID>() {
            @Override
            public OID answer(InvocationOnMock invocation)
                    throws Throwable
            {
                return OID.generate();
            }
        });

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
                        mds.move(from.toStringFormal(), toParent.append(name).toStringFormal(), t);
                        return (SOID)args[0];
                    }
                }
        );
    }

    /**
     * Helper to create SOID matcher
     */
    SOID soidAt(String path)
    {
        return argThat(new IsSOIDAtPath(ds, path));
    }

    FIDAndType generateFileFnt() throws SQLException
    {
        return generateFileFnt(null);
    }

    FIDAndType generateDirFnt() throws SQLException
    {
        return generateDirFnt(null);
    }

    FIDAndType generateDirFnt(@Nullable SOID soid) throws SQLException
    {
        return new FIDAndType(generateFID(soid), true);
    }

    FIDAndType generateFileFnt(@Nullable SOID soid) throws SQLException
    {
        return new FIDAndType(generateFID(soid), false);
    }

    FID generateFID(@Nullable SOID soid) throws SQLException
    {
        byte[] bs = new byte[dr.getFIDLength()];
        Util.rand().nextBytes(bs);
        FID fid = new FID(bs);
        if (soid != null) when(ds.getSOIDNullable_(eq(fid))).thenReturn(soid);
        return fid;
    }

    void fileModified(Path path) throws Exception
    {
        fileModified(path, true);
    }

    void fileModified(Path path, boolean modified) throws Exception
    {
        fileModified(path, path.toStringFormal(), modified);
    }

    void fileModified(Path logical, String physical, boolean modified) throws Exception
    {
        SOID soid = ds.resolveNullable_(logical);

        InjectableFile f = mock(InjectableFile.class);
        CA ca = ds.getOA_(soid).caMaster();
        when(f.wasModifiedSince(ca.mtime(), ca.length())).thenReturn(modified);
        when(factFile.create(Util.join(cfgAbsRootanchor.get(), physical))).thenReturn(f);
    }

    private void op(String path, FIDAndType fnt, Operation op, Operation... flags) throws Exception
    {
        PathCombo pc = new PathCombo(cfgAbsRootanchor, Path.fromString(path));
        SOID src = ds.getSOIDNullable_(fnt._fid);
        SOID dst = ds.resolveNullable_(pc._path);
        l.info("mcop {} {}", src, dst);
        mcop.executeOperation_(EnumSet.of(op, flags), src, dst, pc, fnt, delBuffer, t);
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
        Path path = Path.fromString("foo/bar/hello");
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
        Path path = Path.fromString("foo/bar");
        SOID soid = ds.resolveNullable_(path);
        FIDAndType fnt = generateDirFnt(soid);

        op("foo/bar", fnt, Update);

        verify(delBuffer).remove_(soid);
        verifyZeroInteractions(oc, om, vu);
    }

    @Test
    public void shouldRenameFile() throws Exception
    {
        Path path = Path.fromString("foo/bar/hello");
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
        Path path = Path.fromString("foo/bar/hello");
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
        Path path = Path.fromString("baz");
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
        Path path = Path.fromString("foo/bar/hello");
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
        Path path = Path.fromString("foo/bar");
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

    }

    @Test
    public void shouldRandomizeFID() throws Exception
    {

    }

    @Test
    public void shouldCleanupSource() throws Exception
    {

    }
}
