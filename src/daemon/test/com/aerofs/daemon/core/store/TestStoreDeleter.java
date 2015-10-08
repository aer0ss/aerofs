package com.aerofs.daemon.core.store;

import static org.mockito.Mockito.*;

import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.LogicalStagingArea;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.mock.logical.MockDS.MockDSAnchor;
import com.aerofs.daemon.lib.db.trans.Trans;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.mock.TestUtilCore.ExArbitrary;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.testlib.AbstractTest;
import org.mockito.verification.VerificationMode;

public class TestStoreDeleter extends AbstractTest
{
    @Mock DirectoryService ds;
    @Mock IPhysicalStorage ps;
    @Mock StoreHierarchy ss;
    @Mock IMapSIndex2SID sidx2sid;
    @Mock StoreDeletionOperators _operators;
    @InjectMocks LogicalStagingArea sa = mock(LogicalStagingArea.class);
    @Mock Trans t;

    @InjectMocks StoreDeleter sd;

    MockDS mds;

    SID rootSID = SID.generate();

    SIndex sidxRoot;
    SIndex sidx;
    SIndex sidxChild;
    SIndex sidxChildExpelled;
    SIndex sidxGrandChild;
    SIndex sidxGrandChildExpelled;
    SIndex sidxExternal;

    @Before
    public void setup() throws Exception
    {
        mds = new MockDS(rootSID, ds, null, sidx2sid, ss);
        MockDSAnchor s = mds.root().anchor("s");
        MockDSAnchor c = s.root().anchor("c");
        MockDSAnchor ce = s.root().anchor("ce", true);

        MockDSAnchor gc = c.root().anchor("gc");
        MockDSAnchor gce = c.root().anchor("gce", true);

        sidxRoot = mds.root().soid().sidx();
        sidx = s.root().soid().sidx();
        sidxChild = c.root().soid().sidx();
        sidxChildExpelled = ce.root().soid().sidx();
        sidxGrandChild = gc.root().soid().sidx();
        sidxGrandChildExpelled = gce.root().soid().sidx();

        SID external = SID.generate();
        mds.root(external).file("f1");
        sidxExternal = mds.root(external).soid().sidx();
    }

    @Test
    public void shouldDeleteSelfAndChildStores() throws Exception
    {
        delete(PhysicalOp.APPLY);

        verifyStoreDeletion(sidx);
        verifyStoreDeletion(sidxChild);
        verifyStoreDeletion(sidxGrandChild);
        verifyStoreDeletion(sidxChildExpelled, never());
        verifyStoreDeletion(sidxGrandChildExpelled, never());
    }

    @Test
    public void shouldDeletePhysicalObjects() throws Exception
    {
        ResolvedPath p = ds.resolve_(new SOID(sidxGrandChild, OID.ROOT));

        delete(PhysicalOp.APPLY);

        verify(ps).deleteFolderRecursively_(p, PhysicalOp.APPLY, t);
        verify(ps).deleteFolderRecursively_(p.parent(), PhysicalOp.APPLY, t);
        verify(ps).deleteFolderRecursively_(p.parent().parent(), PhysicalOp.APPLY, t);
    }

    @Test
    public void shouldNotDeleteExpelledAnchorsAndFolders() throws Exception
    {
        ResolvedPath ce = ds.resolve_(new SOID(sidxChildExpelled, OID.ROOT));
        ResolvedPath gce = ds.resolve_(new SOID(sidxGrandChildExpelled, OID.ROOT));

        delete(PhysicalOp.APPLY);

        verify(ps, never()).deleteFolderRecursively_(eq(gce), any(PhysicalOp.class), eq(t));
        verify(ps, never()).deleteFolderRecursively_(eq(ce), any(PhysicalOp.class), eq(t));
    }

   @Test
    public void shouldNotDeleteExternalFolder() throws Exception
    {
        SOID soid = new SOID(sidxExternal, OID.ROOT);
        ResolvedPath ce = ds.resolve_(soid);
        ResolvedPath gce = ds.resolve_(soid);

        when(ss.isRoot_(sidxExternal)).thenReturn(true);
        sd.deleteRootStore_(sidxExternal, PhysicalOp.MAP, t);
    }

    @Test (expected = ExArbitrary.class)
    public void shouldThrowOnException() throws Exception
    {
        doThrow(new ExArbitrary()).when(ps)
                .deleteFolderRecursively_(any(ResolvedPath.class), eq(PhysicalOp.APPLY), eq(t));

        delete(PhysicalOp.APPLY);
    }

    private void delete(PhysicalOp op) throws Exception
    {
        sd.removeParentStoreReference_(sidx, sidxRoot, ds.resolve_(new SOID(sidx, OID.ROOT)), op, t);
    }

    private void verifyStoreDeletion(SIndex sidx) throws Exception
    {
        verifyStoreDeletion(sidx, times(1));
    }

    private void verifyStoreDeletion(SIndex sidx, VerificationMode mode) throws Exception
    {
        verify(sa, mode).stageCleanup_(eq(new SOID(sidx, OID.ROOT)), any(ResolvedPath.class), anyString(), eq(t));
        verify(_operators, mode).runAllImmediate_(sidx, t);
    }
}
