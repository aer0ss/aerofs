package com.aerofs.daemon.core.multiplicity.singleuser.migration;

import java.sql.SQLException;

import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.daemon.core.Hasher;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.ds.ResolvedPathTestUtil;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserStoreHierarchy;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.*;
import com.aerofs.testlib.AbstractTest;

import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static com.aerofs.daemon.core.mock.TestUtilCore.*;
import static com.aerofs.daemon.core.ds.OA.Type;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TestImmigrantDetector extends AbstractTest
{
    @Mock DirectoryService ds;
    @Mock NativeVersionControl nvc;
    @Mock ImmigrantVersionControl ivc;
    @Mock Hasher hasher;
    @Mock IPhysicalStorage ps;
    @Mock SingleuserStoreHierarchy sss;
    @Mock IMapSID2SIndex sid2sidx;
    @Mock IMapSIndex2SID sidx2sid;
    @Mock ObjectDeleter od;
    @Mock AdmittedObjectLocator aol;

    @Mock Trans t;
    @Mock OA oaFrom;
    @Mock OA oaFromExpelled1;
    @Mock OA oaFromExpelled2;
    @Mock OA oaTo;
    @Mock OA oaAnchoredRoot;

    @InjectMocks SingleuserImmigrantDetector imd;

    static final SID rootSID = SID.generate();

    SIndex sidxParent = new SIndex(-123);
    OID oid = SID.storeSID2anchorOID(SID.generate());
    SIndex sidxFrom = new SIndex(3);
    SIndex sidxFromExpelled1 = new SIndex(1);
    SIndex sidxFromExpelled2 = new SIndex(4);
    SIndex sidxTo = new SIndex(2);
    SIndex sidxAnchored = new SIndex(99);
    SOID soidFrom = new SOID(sidxFrom, oid);
    SOID soidFromExpelled1  = new SOID(sidxFromExpelled1, oid);
    SOID soidFromExpelled2 = new SOID(sidxFromExpelled2, oid);
    SOID soidTo = new SOID(sidxTo, oid);
    SOID soidAnchoredRoot = new SOID(sidxAnchored, OID.ROOT);
    SID sidFrom = SID.generate();
    SID sidTo = SID.generate();
    SID sidAnchored = SID.anchorOID2storeSID(oid);
    ResolvedPath pFrom = ResolvedPathTestUtil.fromString(rootSID, "from", soidFrom);
    ResolvedPath pTo = ResolvedPathTestUtil.fromString(rootSID, "to", soidTo);
    Version vKMLFrom = Version.of(DID.generate(), new Tick(100));
    Version vKMLTo = Version.of(DID.generate(), new Tick(100));
    PhysicalOp op = PhysicalOp.APPLY;

    @Before
    public void setUp() throws SQLException, ExNotFound
    {
        // set up basic wiring

        when(nvc.getAllLocalVersions_(any(SOCID.class))).thenReturn(Version.empty());
        mockOA(oaFrom, soidFrom, Type.FILE, false, null, null, ds);
        mockOA(oaFromExpelled1, soidFromExpelled1, Type.FILE, true, null, null, ds);
        mockOA(oaFromExpelled2, soidFromExpelled2, Type.FILE, true, null, null, ds);
        mockOA(oaTo, soidTo, Type.FILE, false, null, null, ds);
        mockOA(oaAnchoredRoot, soidAnchoredRoot, Type.DIR, false, null, null, ds);

        mockStore(null, sidFrom, sidxFrom, sidxParent, sss, null, sid2sidx, sidx2sid);
        mockStore(null, sidTo, sidxTo, sidxParent, sss, null, sid2sidx, sidx2sid);
        mockStore(null, sidAnchored, sidxAnchored, sidxFrom, sss, null, sid2sidx, sidx2sid);

        when(ds.resolve_(oaFrom)).thenReturn(pFrom);
        when(ds.resolve_(oaTo)).thenReturn(pTo);

        when(ps.newFile_(any(ResolvedPath.class), any(KIndex.class))).then(RETURNS_MOCKS);
        when(ps.newFolder_(any(ResolvedPath.class))).then(RETURNS_MOCKS);

        SOCID socidFrom = new SOCID(soidFrom, CID.CONTENT);
        SOCID socidTo = new SOCID(soidTo, CID.CONTENT);
        when(nvc.getKMLVersion_(any(SOCID.class))).thenReturn(Version.empty());
        when(nvc.getKMLVersion_(socidFrom)).thenReturn(vKMLFrom);
        when(nvc.getKMLVersion_(socidTo)).thenReturn(vKMLTo);
        when(oaAnchoredRoot.parent()).thenReturn(new OID(UniqueID.generate()));

        when(aol.locate_(eq(oid), eq(sidxTo), any(OA.Type.class))).thenReturn(oaFrom);
    }

    ////////
    // enforcement tests

    @Test(expected = AssertionError.class)
    public void shouldAssertSameObjectType() throws Exception
    {
        mockOA(oaFrom, soidFrom, OA.Type.ANCHOR, false, null, null, ds);
        imd.detectAndPerformImmigration_(oaTo, op, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertTargetIsAdmitted() throws Exception
    {
        mockOA(oaTo, soidTo, Type.FILE, true, null, null, ds);
        imd.detectAndPerformImmigration_(oaTo, op, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertTargetIsAbsent() throws Exception
    {
        mockBranches(oaTo, 1, 0, 0, nvc);
        imd.detectAndPerformImmigration_(oaTo, op, t);
    }

    ////////
    // logic tests

    @Test
    public void shouldNotMigrateIfNoSourceIsFoundOrAdmitted() throws Exception
    {
        when(aol.locate_(eq(oid), eq(sidxTo), any(OA.Type.class))).thenReturn(null);
        shouldNotMigrate();
    }

    @Test
    public void shouldNotMigrateDir() throws Exception
    {
        mockOA(oaFrom, soidFrom, OA.Type.DIR, false, null, null, ds);
        mockOA(oaTo, soidTo, OA.Type.DIR, false, null, null, ds);
        shouldNotMigrate();

    }

    @Test
    public void shouldMigrateAnchor() throws Exception
    {
        mockOA(oaFrom, soidFrom, OA.Type.ANCHOR, false, null, null, ds);
        mockOA(oaFromExpelled1, soidFromExpelled1, OA.Type.ANCHOR, true, null, null, ds);
        mockOA(oaFromExpelled2, soidFromExpelled2, OA.Type.ANCHOR, true, null, null, ds);
        mockOA(oaTo, soidTo, OA.Type.ANCHOR, false, null, null, ds);

        shouldMigrate();
        verify(sss).addParent_(sidxAnchored, sidxTo, t);
    }

    @Test
    public void shouldMigrateFileContentAttributes() throws Exception
    {
        int branches = 5;
        mockBranches(oaFrom, branches, 0, 0, nvc);

        shouldMigrate();

        verify(ds, times(branches)).createCA_(eq(soidTo), (KIndex) any(), eq(t));
    }

    @Test
    public void shouldMigrateLocalVersions() throws Exception
    {
        int branches = 4;
        mockBranches(oaFrom, branches, 0, 0, nvc);

        shouldMigrate();

        Version vLocalSum = Version.empty();
        SOCID socidTo = new SOCID(soidTo, CID.CONTENT);
        for (KIndex kidx : oaFrom.cas().keySet()) {
            SOCKID kFrom = new SOCKID(soidFrom, CID.CONTENT, kidx);
            SOCKID kTo = new SOCKID(socidTo, kidx);
            Version vLocalFrom = nvc.getLocalVersion_(kFrom);
            vLocalSum = vLocalSum.add_(vLocalFrom);
            verify(nvc).addLocalVersion_(kTo, vLocalFrom, t);
        }

        verify(ivc).createLocalImmigrantVersions_(socidTo, vLocalSum, t);
    }

    @Test
    public void shouldDeleteLocalFromKMLVersion() throws Exception
    {
        int branches = 3;
        mockBranches(oaFrom, branches, 0, 0, nvc);

        shouldMigrate();

        // intersect of localFrom and kmlTo is empty, so we verify that
        // empty kml is deleted
        SOCID socidTo = new SOCID(soidTo, CID.CONTENT);
        verify(nvc).deleteKMLVersion_(socidTo, Version.empty(), t);
    }

    @Test
    public void shouldNotMigrateKMLVersion() throws Exception
    {
        shouldMigrate();

        SOCID socidTo = new SOCID(soidTo, CID.CONTENT);
        verify(ivc, never()).createLocalImmigrantVersions_(eq(socidTo), eq(vKMLFrom),
                any(Trans.class));
    }

    private void shouldNotMigrate() throws Exception
    {
        assertFalse(imd.detectAndPerformImmigration_(oaTo, op, t));
        verify(od, never()).deleteAndEmigrate_((SOID)any(), any(PhysicalOp.class), (SID)any(),
                (Trans)any());
        verify(od, never()).delete_(any(SOID.class), any(PhysicalOp.class), any(Trans.class));
    }

    private void shouldMigrate() throws Exception
    {
        assertTrue(imd.detectAndPerformImmigration_(oaTo, op, t));
        verify(od).deleteAndEmigrate_(eq(soidFrom), any(PhysicalOp.class), (SID)any(), (Trans)any());

        for (SOID s : ImmutableSet.of(soidFromExpelled1, soidFromExpelled2)) {
            verify(od, never()).deleteAndEmigrate_(eq(s), any(PhysicalOp.class), (SID)any(),
                    (Trans)any());
            verify(od, never()).delete_(eq(s), any(PhysicalOp.class), (Trans)any());
        }
    }
}
