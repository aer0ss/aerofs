/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.class_under_test;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.activity.OutboundEventLogger;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.migration.IEmigrantTargetSIDLister;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.protocol.GetComponentRequest;
import com.aerofs.daemon.core.protocol.LegacyContentSender;
import com.aerofs.daemon.core.protocol.PrefixVersionControl;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.cfg.CfgStorageType;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class contains a NewUpdates object with its supporting mock objects
 */
public class GetComponentRequestWithMocks extends AbstractClassUnderTestWithMocks
{
    public final IEmigrantTargetSIDLister _emc = mock(IEmigrantTargetSIDLister.class);
    public final PrefixVersionControl _pvc = mock(PrefixVersionControl.class);
    public final NativeVersionControl _nvc = mock(NativeVersionControl.class);
    public final MapAlias2Target _a2t = mock(MapAlias2Target.class);
    public final DirectoryService _ds = mock(DirectoryService.class);
    public final IPhysicalStorage _ps = mock(IPhysicalStorage.class);
    public final OutboundEventLogger _oel = mock(OutboundEventLogger.class);
    public final TransManager _tm = mock(TransManager.class);

    // For GCCContentSender
    public final UploadState _ulstate = mock(UploadState.class);
    public final TokenManager _tokenManager = mock(TokenManager.class);

    private final CoreScheduler _sched = mock(CoreScheduler.class);

    public final LegacyContentSender _gccContentSender =
            new LegacyContentSender(_ulstate, _sched, _trl, _m, _tokenManager, new CfgStorageType());
    public final GetComponentRequest _gcc = new GetComponentRequest();

    public GetComponentRequestWithMocks()
    {
        when(_tm.begin_()).thenAnswer(invocation -> mock(Trans.class));
        _gcc.inject_(_trl, _lacl, _ps, _oel, _ds, _rpc, _pvc, _nvc, _emc, _gccContentSender, _a2t,
                _sidx2sid, _sid2sidx, _cfgLocalUser, _tm);
    }
}
