package com.aerofs.daemon.core.multiplicity.singleuser.migration;

import java.sql.SQLException;
import java.util.*;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.daemon.core.migration.EmigrantUtil;
import com.aerofs.daemon.core.mock.TestUtilCore;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.protocol.dependence.DependencyEdge;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.*;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.protocol.Downloads;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.tc.Token;
import com.google.protobuf.ByteString;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static com.aerofs.daemon.core.mock.TestUtilCore.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class TestEmigrantDetector extends AbstractTest
{
    static class ExArbitrary extends Exception {
        private static final long serialVersionUID = 1L;
    }

    @Mock DirectoryService ds;
    @Mock Downloads dls;
    @Mock To.Factory factTo;
    @Mock IMapSID2SIndex sid2sidx;

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
    SOCID socidSource = new SOCID(soidSource, CID.META);
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
        HashSet<OID> children = new HashSet<OID>();
        children.add(new OID(UniqueID.generate()));
        children.add(new OID(UniqueID.generate()));
        children.add(new OID(UniqueID.generate()));
        when(ds.getChildren_(soidSource)).thenReturn(children);
    }

    /**
     * Repeated uses of this class require the use of doAnswer().when or doThrow().when
     * instead of when(...).thenAnswer, etc., to avoid an NPE
     * http://stackoverflow.com/questions/10342461/mockito-acts-strangely-when-i-assign-multiple-custom-matchers-to-a-single-method
     */
    private class IsDependencyWithDestination extends ArgumentMatcher<DependencyEdge>
    {
        private final SOCID _expectedDst;
        IsDependencyWithDestination(SOCID expectedDst)
        {
            _expectedDst = expectedDst;
        }

        @Override
        public boolean matches(Object o)
        {
            DependencyEdge dep = (DependencyEdge) o;
            return _expectedDst.equals(dep.dst);
        }
    }

    @Before
    public void setup() throws Exception
    {
        oidParentTo = OID.TRASH;
        nameTo = EmigrantUtil.getDeletedObjectName_(soidSource, sidTarget);
        mockOA(oa, soidSource, Type.FILE, false, oidSourceParentFrom, nameSourceFrom);

        mockStore(null, sidSource, sidxSource, sidxRoot, null, null, sid2sidx, null);

        // Target and target-parent stores are absent
        mockStore(null, sidTargetGrandParent, sidxTargetGrandParent, sidxRoot, null, null, sid2sidx, null);

        sidsTargetAncestor = new ArrayList<ByteString>();
        sidsTargetAncestor.add(sidTargetParent.toPB());
        sidsTargetAncestor.add(sidTargetGrandParent.toPB());

        when(ds.getOANullable_(soidSource)).thenReturn(oa);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock)
                    throws Throwable
            {
                mockStore(null, sidTargetParent, sidxTargetParent, sidxTargetGrandParent, null,
                        null, sid2sidx, null);
                return null;
            }
        }).when(dls).downloadSync_(argThat(new IsDependencyWithDestination(socidAnchorTargetParent)),
                any(To.class), any(Token.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock)
                    throws Throwable
            {
                mockStore(null, sidTarget, sidxTarget, sidxTargetParent, null, null, sid2sidx,
                        null);
                return null;
            }
        }).when(dls).downloadSync_(argThat(new IsDependencyWithDestination(socidAnchorTarget)),
                any(To.class), any(Token.class));
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
            when(dls.downloadSync_(eq(socidChild), any(To.class), eq(tk), eq(socidSource)))
                    .thenThrow(new ExArbitrary());
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

        InOrder inOrder = inOrder(dls);
        inOrder.verify(dls).downloadSync_(
                eq(new DependencyEdge(socidSource, socidAnchorTargetParent)), any(To.class),
                eq(tk));
        inOrder.verify(dls).downloadSync_(
                eq(new DependencyEdge(socidSource, socidAnchorTarget)), any(To.class), eq(tk));
        inOrder.verify(dls).downloadSync_(eq(socidTarget), any(To.class), eq(tk), eq(socidSource));
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
        reset(dls);
        doThrow(new ExArbitrary()).when(dls).downloadSync_(
                argThat(new IsDependencyWithDestination(socidAnchorTargetParent)), any(To.class),
                any(Token.class));
        shouldEmigrate();
    }

    private void shouldEmigrate() throws Exception
    {
        emd.detectAndPerformEmigration_(soidSource, oidParentTo, nameTo, sidsTargetAncestor, did,
                tk);
        verifyDownload(socidTarget);
    }

    private void shouldNotEmigrate() throws Exception
    {
        emd.detectAndPerformEmigration_(soidSource, oidParentTo, nameTo, sidsTargetAncestor, did,
                tk);
        verifyNotDownload(socidTarget);
    }

    private void verifyDownload(SOCID socid) throws Exception
    {
        verify(dls).downloadSync_(eq(socid), any(To.class), eq(tk), eq(socidSource));
    }

    private void verifyNotDownload(SOCID k) throws Exception
    {
        verify(dls, never()).downloadSync_(eq(k), any(To.class),
                any(Token.class), any(SOCID.class));
    }
}
