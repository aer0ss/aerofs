package com.aerofs.daemon.core.migration;

import java.sql.SQLException;
import java.util.Map;

import com.aerofs.daemon.core.Hasher;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.*;
import com.aerofs.testlib.AbstractTest;

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
    @Mock IStores ss;
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

    @InjectMocks ImmigrantDetector imd;

    SIndex sidxParent = new SIndex(-123);
    OID oid = new OID(UniqueID.generate());
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
    SID sidFrom = new SID(UniqueID.generate());
    SID sidTo = new SID(UniqueID.generate());
    SID sidAnchored = SID.anchorOID2storeSID(oid);
    Path pFrom = new Path("fooFrom", "barFrom", "bazFrom");
    Path pTo = new Path("fooTo", "barTo", "bazTo");
    Version vKMLFrom = new Version().set_(new DID(UniqueID.generate()), new Tick(100));
    Version vKMLTo = new Version().set_(new DID(UniqueID.generate()), new Tick(100));
    PhysicalOp op = PhysicalOp.APPLY;

    @Before
    public void setUp() throws SQLException, ExNotFound
    {
        // set up basic wiring
        mockOA(oaFrom, soidFrom, Type.FILE, false, null, null, ds);
        mockOA(oaFromExpelled1, soidFromExpelled1, Type.FILE, true, null, null, ds);
        mockOA(oaFromExpelled2, soidFromExpelled2, Type.FILE, true, null, null, ds);
        mockOA(oaTo, soidTo, Type.FILE, false, null, null, ds);
        mockOA(oaAnchoredRoot, soidAnchoredRoot, Type.DIR, false, null, null, ds);

        mockStore(null, sidFrom, sidxFrom, sidxParent, ss, null, sid2sidx, sidx2sid);
        mockStore(null, sidTo, sidxTo, sidxParent, ss, null, sid2sidx, sidx2sid);
        mockStore(null, sidAnchored, sidxAnchored, sidxFrom, ss, null, sid2sidx, sidx2sid);

        when(ds.resolveNullable_(soidFrom)).thenReturn(pFrom);
        when(ds.resolveNullable_(soidTo)).thenReturn(pTo);
        when(nvc.getKMLVersion_(new SOCID(soidFrom, CID.CONTENT))).thenReturn(vKMLFrom);
        when(nvc.getKMLVersion_(new SOCID(soidTo, CID.CONTENT))).thenReturn(vKMLTo);
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
        verify(ss).setParent_(sidxAnchored, sidxTo, t);
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

        Version vLocalSum = new Version();
        SOCID socidTo = new SOCID(soidTo, CID.CONTENT);
        for (KIndex kidx : oaFrom.cas().keySet()) {
            SOCKID kFrom = new SOCKID(soidFrom, CID.CONTENT, kidx);
            SOCKID kTo = new SOCKID(socidTo, kidx);
            Version vLocalFrom = nvc.getLocalVersion_(kFrom);
            vLocalSum = vLocalSum.add_(vLocalFrom);
            verify(nvc).addLocalVersion_(kTo, vLocalFrom, t);
        }

        for (Map.Entry<DID, Tick> en : vLocalSum.getAll_().entrySet()) {
            DID did = en.getKey();
            Tick tick = en.getValue();
            verify(ivc).updateMyImmigrantVersion_(socidTo, did, tick, t);
        }
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
        verify(nvc).deleteKMLVersion_(socidTo, new Version(), t);
    }

    @Test
    public void shouldNotMigrateKMLVersion() throws Exception
    {
        shouldMigrate();

        SOCID socidTo = new SOCID(soidTo, CID.CONTENT);
        for (Map.Entry<DID, Tick> en : vKMLFrom.getAll_().entrySet()) {
            DID did = en.getKey();
            Tick tick = en.getValue();
            verify(ivc, never()).updateMyImmigrantVersion_(eq(socidTo), eq(did), eq(tick),
                    any(Trans.class));
        }
    }

    private void shouldNotMigrate() throws Exception
    {
        assertFalse(imd.detectAndPerformImmigration_(oaTo, op, t));
        verify(od, never()).delete_((SOID) any(), any(PhysicalOp.class), (SID) any(), (Trans) any());
    }

    private void shouldMigrate() throws Exception
    {
        assertTrue(imd.detectAndPerformImmigration_(oaTo, op, t));
        verify(od).delete_(eq(soidFrom), any(PhysicalOp.class), (SID) any(), (Trans) any());
        verify(od, never()).delete_(eq(soidFromExpelled1), any(PhysicalOp.class),
                (SID) any(), (Trans) any());
        verify(od, never()).delete_(eq(soidFromExpelled2), any(PhysicalOp.class),
                (SID) any(), (Trans) any());
    }
}
