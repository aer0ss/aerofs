package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.ds.ResolvedPathTestUtil;
import com.aerofs.daemon.core.first_launch.OIDGenerator;
import com.aerofs.daemon.core.mock.physical.MockPhysicalTree;
import com.aerofs.daemon.core.phy.linked.RepresentabilityHelper;
import com.aerofs.daemon.core.phy.linked.RepresentabilityHelper.Representability;
import com.aerofs.daemon.core.phy.linked.linker.ILinkerFilter.AcceptAll;
import com.aerofs.daemon.core.phy.linked.linker.MightCreate.Result;
import com.aerofs.daemon.core.mock.logical.MockDir;
import com.aerofs.daemon.core.mock.logical.MockFile;
import com.aerofs.daemon.core.mock.logical.MockRoot;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.testlib.AbstractTest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Set;

import static com.aerofs.daemon.core.mock.physical.MockPhysicalTree.dir;
import static com.aerofs.daemon.core.mock.physical.MockPhysicalTree.file;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.*;
import static org.mockito.Mockito.*;

/**
 * Subclasses of this class test MightCreate. Each subclass tests a certain combination
 * of relations between a logical file and a physical file. For example,
 * {@code TestMightCreate_DiffFIDSamePathDiffType} tests the case where a physical file has a
 * different FID, the same path, and a different file type (file vs directory) with a logical file.
 * Because MightCreate's implementation is modeled after these combinations, we test it with
 * the same model.
 */
public abstract class AbstractTestMightCreate extends AbstractTest
{
    @Mock IgnoreList il;
    @Mock Trans t;
    @Mock TransManager tm;
    @Mock SIDMap sm;
    @Mock InjectableFile.Factory factFile;
    @Mock InjectableDriver dr;
    @Mock CfgLocalUser cfgUser;
    @Mock IDeletionBuffer delBuffer;
    @Mock SharedFolderTagFileAndIcon sfti;
    @Mock MightCreateOperations mcop;
    @Mock DirectoryService ds;
    @Mock LinkerRootMap lrm;
    @Mock IOSUtil osutil;

    MightCreate mc;

    MockPhysicalTree osRoot =
            dir("root",
                    file("f1"),
                    file("F1"),
                    file("f2"),
                    file("f2 (3)"),
                    dir("d3"),
                    dir("d4"),
                    file("ignored"),
                    file("f5")
            );

    MockRoot logicRoot =
            new MockRoot(
                    new MockFile("f1", 2),
                    new MockDir("f2"),
                    new MockDir("f2 (2)"),
                    new MockDir("d4"),
                    new MockFile("f5", 0)   // a file with no master branch
            );

    final SID rootSID = SID.generate();
    final String pRoot = Util.join("root");
    final OIDGenerator og = new OIDGenerator(SID.generate(), "dummy");

    @SuppressWarnings("unchecked")
    @Before
    public void setupAbstractClass() throws Exception
    {
        when(tm.begin_()).thenReturn(t);

        when(il.isIgnored("ignored")).thenReturn(true);

        when(mcop.executeOperation_(anySetOf(Operation.class), any(SOID.class), any(SOID.class),
                any(PathCombo.class), any(FIDAndType.class), any(IDeletionBuffer.class),
                any(OIDGenerator.class), eq(t)))
                .thenAnswer(new Answer<Boolean>() {
                    @Override
                    public Boolean answer(InvocationOnMock invocation) throws Throwable
                    {
                        return !Sets.intersection((Set<Operation>)invocation.getArguments()[0],
                                EnumSet.of(Operation.CREATE, Operation.REPLACE)).isEmpty();
                    }
                });

        when(lrm.absRootAnchor_(rootSID)).thenReturn(pRoot);
        LinkerRoot root = mock(LinkerRoot.class);
        when(root.sid()).thenReturn(rootSID);
        when(lrm.getAllRoots_()).thenReturn(ImmutableList.of(root));
        when(lrm.isPhysicallyEquivalent_(any(Path.class), any(Path.class)))
                .thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation)
                    throws Throwable
            {
                Object[] args = invocation.getArguments();
                return args[0].equals(args[1]);
            }
        });

        when(osutil.isInvalidFileName(anyString())).thenReturn(false);

        RepresentabilityHelper rh = mock(RepresentabilityHelper.class);
        when(rh.representability_(any(OA.class))).thenReturn(Representability.REPRESENTABLE);

        mc = new MightCreate(il, ds, dr, sfti, mcop, lrm, new AcceptAll(),
                rh, osutil);

        osRoot.mock(factFile, dr);
        logicRoot.mock(rootSID, ds, null, null);
    }

    ResolvedPath mkpath(String path)
    {
        return ResolvedPathTestUtil.fromString(rootSID, path);
    }

    /**
     * assign the FID to the object specified by the SOID
     */
    protected void assign(SOID soid, FID fid) throws IOException, SQLException
    {
        OA oa = ds.getOANullable_(soid);
        when(ds.getSOIDNullable_(fid)).thenReturn(soid);
        when(oa.fid()).thenReturn(fid);
    }

    protected Result mightCreate(String physicalObj)
            throws Exception
    {
        return mc.mightCreate_(new PathCombo(rootSID, pRoot, Util.join(pRoot, physicalObj)),
                delBuffer, og, t);
    }


    protected void verifyOperationExecuted(Set<Operation> ops) throws Exception
    {
        verify(mcop).executeOperation_(eq(ops), any(SOID.class), any(SOID.class),
                any(PathCombo.class), any(FIDAndType.class), eq(delBuffer), eq(og), eq(t));
    }

    protected void verifyOperationExecuted(Operation op, String path) throws Exception
    {
        verifyOperationExecuted(EnumSet.of(op), path);
    }

    protected void verifyOperationExecuted(Set<Operation> ops, String path) throws Exception
    {
        PathCombo pc = new PathCombo(pRoot, Path.fromString(rootSID, path));
        FIDAndType fnt = dr.getFIDAndTypeNullable(pc._absPath);

        verify(mcop).executeOperation_(eq(ops), any(SOID.class), any(SOID.class),
                eq(pc), eq(fnt), eq(delBuffer), eq(og), eq(t));
    }

    protected void verifyOperationExecuted(Operation op, SOID source, SOID target, String path)
            throws Exception
    {
        verifyOperationExecuted(EnumSet.of(op), source, target, path);
    }

    protected void verifyOperationExecuted(Set<Operation> ops, SOID source, SOID target,
            String path) throws Exception
    {
        PathCombo pc = new PathCombo(pRoot, Path.fromString(rootSID, path));
        FIDAndType fnt = dr.getFIDAndTypeNullable(pc._absPath);

        verify(mcop).executeOperation_(eq(ops), eq(source), eq(target), eq(pc), eq(fnt),
                eq(delBuffer), eq(og), eq(t));
    }
}
