/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.dependence;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.net.ReceiveAndApplyUpdate;
import com.aerofs.daemon.core.net.ReceiveAndApplyUpdate.CausalityResult;
import com.aerofs.daemon.core.net.proto.MetaDiff;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.proto.Core.PBMeta;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class TestDownloadDeadlockResolver extends AbstractTest
{
    @Mock private DirectoryService _ds;
    @Mock private TransManager _tm;
    @Mock private ReceiveAndApplyUpdate _ru;
    @Mock MetaDiff _mdiff;

    @InjectMocks private DownloadDeadlockResolver _ddr;

    private final SIndex _sidx = new SIndex(1);
    private final SOCID _socidLocalChild = new SOCID(_sidx, new OID(UniqueID.generate()), CID.META);
    private final SOCID _socidRemAncestor = new SOCID(_sidx, new OID(UniqueID.generate()), CID.META);
    private final OID _oidCommonParent = new OID(UniqueID.generate());
    private final DID _did = new DID(UniqueID.generate());

    @Mock Trans _t;

    @Before
    public void setup() throws Exception
    {
        OA oaChild = mock(OA.class);
        when(_ds.getOA_(_socidLocalChild.soid())).thenReturn(oaChild);
        when(oaChild.parent()).thenReturn(_oidCommonParent);

        when(_tm.begin_()).thenReturn(_t);

        when(_ru.computeCausalityForMeta_(any(SOID.class), any(Version.class), anyInt()))
                .thenReturn(mock(CausalityResult.class));
    }

    @Test
    public void shouldRenameDependeeWhenNameConflictIsFirstInCycleWithOneParentDependency()
            throws Exception
    {
        ImmutableList<DependencyEdge> cycle = ImmutableList.of(
                newNameConflictDependency(_socidRemAncestor, _socidLocalChild),
                new ParentDependencyEdge(_socidLocalChild, _socidRemAncestor));

        _ddr.resolveDeadlock_(cycle);

        verifyThatNameConflictIsResolvedByRenaming();
    }

    @Test
    public void shouldRenameDependeeWhenNameConflictIsLastInCycleWithOneParentDependency()
            throws Exception
    {
        ImmutableList<DependencyEdge> cycle = ImmutableList.of(
                new ParentDependencyEdge(_socidLocalChild, _socidRemAncestor),
                newNameConflictDependency(_socidRemAncestor, _socidLocalChild));

        _ddr.resolveDeadlock_(cycle);

        verifyThatNameConflictIsResolvedByRenaming();
    }


    @Test
    public void shouldRenameDependeeWhenNameConflictIsMiddleInCycleWithOneParentDependency()
            throws Exception
    {
        SOCID socidMiddle = new SOCID(_sidx, new OID(UniqueID.generate()), CID.META);
        ImmutableList<DependencyEdge> cycle = ImmutableList.of(
                new ParentDependencyEdge(socidMiddle, _socidRemAncestor),
                newNameConflictDependency(_socidRemAncestor, _socidLocalChild),
                new ParentDependencyEdge(_socidLocalChild, socidMiddle));

        _ddr.resolveDeadlock_(cycle);

        verifyThatNameConflictIsResolvedByRenaming();
    }

    private void verifyThatNameConflictIsResolvedByRenaming()
            throws Exception
    {
        verify(_ru).resolveNameConflictByRenaming_(eq(_did), eq(_socidRemAncestor.soid()),
                eq(_socidLocalChild.soid()), anyBoolean(), eq(_oidCommonParent), any(Path.class),
                any(Version.class), any(PBMeta.class), anyInt(), any(SOID.class),
                any(CausalityResult.class), eq(_t));
    }

    private NameConflictDependencyEdge newNameConflictDependency(SOCID src, SOCID dst)
    {
        return new NameConflictDependencyEdge(src, dst, _did, _oidCommonParent, mock(Version.class),
                null, mock(SOID.class));
    }
}
