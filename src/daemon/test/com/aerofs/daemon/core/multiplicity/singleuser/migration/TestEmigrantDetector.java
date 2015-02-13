package com.aerofs.daemon.core.multiplicity.singleuser.migration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.migration.EmigrantUtil;
import com.aerofs.daemon.core.mock.TestUtilCore;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.transfers.download.IDownloadContext;
import com.aerofs.daemon.core.transfers.download.dependence.DependencyEdge.DependencyType;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.aerofs.testlib.AbstractTest;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static com.aerofs.daemon.core.mock.TestUtilCore.mockAbsentStore;
import static com.aerofs.daemon.core.mock.TestUtilCore.mockStore;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestEmigrantDetector extends AbstractTest
{
    static class ExArbitrary extends Exception {
        private static final long serialVersionUID = 1L;
    }

    @Mock DirectoryService ds;
    @Mock To.Factory factTo;
    @Mock IMapSID2SIndex sid2sidx;

    @Mock IDownloadContext cxt;
    @Mock Token tk;
    @Mock DID did;
    @Mock OA oa;

    SID sidSource = SID.generate();
    SID sidTarget = SID.generate();
    SID sidTargetParent = SID.generate();
    SID sidTargetGrandParent = SID.generate();
    SIndex sidxSource = new SIndex(1);
    SIndex sidxTarget = new SIndex(2);
    SIndex sidxTargetParent = new SIndex(3);
    SIndex sidxTargetGrandParent = new SIndex(4);
    SIndex sidxRoot = new SIndex(5);

    SOID soidAnchorTarget = new SOID(sidxTargetParent, SID.storeSID2anchorOID(sidTarget));
    SOID soidAnchorTargetParent = new SOID(sidxTargetGrandParent, SID.storeSID2anchorOID(sidTargetParent));

    SOID soidSource = new SOID(sidxSource, new OID(UniqueID.generate()));
    SOID soidTarget = new SOID(sidxTarget, soidSource.oid());

    SOCID socidAnchorTarget = new SOCID(soidAnchorTarget, CID.META);
    SOCID socidAnchorTargetParent = new SOCID(soidAnchorTargetParent, CID.META);
    SOCID socidTarget = new SOCID(soidTarget, CID.META);

    OID oidSourceParentFrom = new OID(UniqueID.generate());
    String nameSourceFrom = "Foo";


    // the following fields are initialized in setup()
    OID oidParentTo;
    String nameTo;
    List<ByteString> sidsTargetAncestor;

    @InjectMocks EmigrantDetector emd;

    private void mockOA(OA oa, SOID soid, OA.Type type, boolean expelled,
            OID parent, String name) throws SQLException
    {
        TestUtilCore.mockOA(oa, soid, type, expelled, null, null, ds);
        when(oa.parent()).thenReturn(parent);
        when(oa.name()).thenReturn(name);
    }

    private void mockChildren() throws ExNotDir, ExNotFound, SQLException
    {
        HashSet<OID> children = new HashSet<>();
        children.add(new OID(UniqueID.generate()));
        children.add(new OID(UniqueID.generate()));
        children.add(new OID(UniqueID.generate()));
        when(ds.getChildren_(soidSource)).thenReturn(children);
    }

    @Before
    public void setup() throws Exception
    {
        when(cxt.token()).thenReturn(tk);

        oidParentTo = OID.TRASH;
        nameTo = EmigrantUtil.getDeletedObjectName_(soidSource, sidTarget);
        mockOA(oa, soidSource, Type.FILE, false, oidSourceParentFrom, nameSourceFrom);

        mockStore(null, sidSource, sidxSource, sidxRoot, null, null, sid2sidx, null);

        // Target and target-parent stores are absent
        mockStore(null, sidTargetGrandParent, sidxTargetGrandParent, sidxRoot, null, null, sid2sidx, null);

        sidsTargetAncestor = new ArrayList<>();
        sidsTargetAncestor.add(BaseUtil.toPB(sidTargetParent));
        sidsTargetAncestor.add(BaseUtil.toPB(sidTargetGrandParent));

        when(ds.getOANullable_(soidSource)).thenReturn(oa);

        doAnswer(invocationOnMock -> {
            mockStore(null, sidTargetParent, sidxTargetParent, sidxTargetGrandParent, null,
                    null, sid2sidx, null);
            return null;
        }).when(cxt).downloadSync_(eq(socidAnchorTargetParent), any(DependencyType.class));

        doAnswer(invocationOnMock -> {
            mockStore(null, sidTarget, sidxTarget, sidxTargetParent, null, null, sid2sidx,
                    null);
            return null;
        }).when(cxt).downloadSync_(eq(socidAnchorTarget), any(DependencyType.class));
    }

    @Test
    public void shouldEmigrateFile() throws Exception
    {
        shouldEmigrate();
    }

    @Test
    public void shouldEmigrateAnchor() throws Exception
    {
        mockOA(oa, soidSource, Type.ANCHOR, false, oidSourceParentFrom,
                nameSourceFrom);
        shouldEmigrate();
    }

    @Test
    public void shouldDownloadChildrenAndNotEmigrateItselfForFolder() throws Exception
    {
        mockOA(oa, soidSource, Type.DIR, false, oidSourceParentFrom,
                nameSourceFrom);
        mockChildren();
        shouldNotEmigrate();
        for (OID child : ds.getChildren_(soidSource)) {
            verifyDownload(new SOCID(sidxSource, child, CID.META));
        }
    }

    // this test case is subject to change. see Comment (A) in EmigrantDetector
    @Test
    public void shouldIgnoreErrorsFromChildrenDownloads() throws Exception
    {
        mockOA(oa, soidSource, Type.DIR, false, oidSourceParentFrom,
                nameSourceFrom);
        mockChildren();
        // mock exceptions
        for (OID child : ds.getChildren_(soidSource)) {
            SOCID socidChild = new SOCID(sidxSource, child, CID.META);
            doThrow(ExArbitrary.class)
                    .when(cxt).downloadSync_(eq(socidChild), eq(DependencyType.UNSPECIFIED));
        }
        shouldNotEmigrate();
        for (OID child : ds.getChildren_(soidSource)) {
            verifyDownload(new SOCID(sidxSource, child, CID.META));
        }
    }

    @Test
    public void shouldDownloadAncestorsAndThenEmigrate() throws Exception
    {
        shouldEmigrate();

        InOrder inOrder = inOrder(cxt);
        inOrder.verify(cxt).downloadSync_(eq(socidAnchorTargetParent), any(DependencyType.class));
        inOrder.verify(cxt).downloadSync_(eq(socidAnchorTarget), any(DependencyType.class));
        inOrder.verify(cxt).downloadSync_(eq(socidTarget), any(DependencyType.class));
    }

    @Test
    public void shouldNotDownloadExistingAncestor() throws Exception
    {
        mockStore(null, sidTargetParent, sidxTargetParent, sidxTargetGrandParent, null, null,
                sid2sidx, null);
        shouldEmigrate();
        verifyNotDownload(socidAnchorTargetParent);
    }

    @Test
    public void shouldNotDownloadExistingAncestor2() throws Exception
    {
        mockStore(null, sidTarget, sidxTarget, sidxTargetGrandParent, null, null, sid2sidx, null);
        shouldEmigrate();
        verifyNotDownload(socidAnchorTarget);
        verifyNotDownload(socidAnchorTargetParent);
    }

    @Test
    public void shouldNotEmigrateIfNewParentIsNotTrash() throws Exception
    {
        oidParentTo = new OID(UniqueID.generate());
        shouldNotEmigrate();
    }

    @Test
    public void shouldNotEmigrateIfNewNameIsNotEmigrantName() throws Exception
    {
        nameTo = "not an emigrant name";
        shouldNotEmigrate();
    }

    @Test
    public void shouldNotEmigrateIfNoTargetAncestorSpecified() throws Exception
    {
        sidsTargetAncestor = Collections.emptyList();
        shouldNotEmigrate();
    }

    @Test
    public void shouldNotEmigrateIfNoCommonAncestorExists() throws Exception
    {
        mockAbsentStore(sidxTargetGrandParent, sidTargetGrandParent, null, sid2sidx, null);
        shouldNotEmigrate();
    }

    @Test(expected = ExArbitrary.class)
    public void shouldThrowIfDownloadingAncestorFailed() throws Exception
    {
        reset(cxt);
        doThrow(new ExArbitrary())
                .when(cxt).downloadSync_(eq(socidAnchorTargetParent), any(DependencyType.class));
        shouldEmigrate();
    }

    private void shouldEmigrate() throws Exception
    {
        emd.detectAndPerformEmigration_(soidSource, oidParentTo, nameTo, sidsTargetAncestor, cxt);
        verifyDownload(socidTarget);
    }

    private void shouldNotEmigrate() throws Exception
    {
        emd.detectAndPerformEmigration_(soidSource, oidParentTo, nameTo, sidsTargetAncestor, cxt);
        verifyNotDownload(socidTarget);
    }

    private void verifyDownload(SOCID socid) throws Exception
    {
        verify(cxt).downloadSync_(eq(socid), any(DependencyType.class));
    }

    private void verifyNotDownload(SOCID socid) throws Exception
    {
        verify(cxt, never()).downloadSync_(eq(socid), any(DependencyType.class));
    }
}
