/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.class_under_test;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.activity.OutboundEventLogger;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.migration.IEmigrantTargetSIDLister;
import com.aerofs.daemon.core.net.OutgoingStreams;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.protocol.GCCContentSender;
import com.aerofs.daemon.core.protocol.GetComponentCall;
import com.aerofs.daemon.core.protocol.PrefixVersionControl;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.rocklog.RockLog;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * This class contains a NewUpdates object with its supporting mock objects
 */
public class GetComponentCallWithMocks extends AbstractClassUnderTestWithMocks
{
    public @Mock IEmigrantTargetSIDLister _emc;
    public @Mock PrefixVersionControl _pvc;
    public @Mock NativeVersionControl _nvc;
    public @Mock MapAlias2Target _a2t;
    public @Mock DirectoryService _ds;
    public @Mock IPhysicalStorage _ps;
    public @Mock OutboundEventLogger _oel;
    public @Mock TransManager _tm;
    public @Mock RockLog _rl;

    // For GCCContentSender
    public @Mock OutgoingStreams _oss;
    public @Mock UploadState _ulstate;
    public @Mock TokenManager _tokenManager;

    public @InjectMocks GCCContentSender _gccContentSender;
    public @InjectMocks GetComponentCall _gcc;

    public GetComponentCallWithMocks()
    {
        // Mockito doesn't inject an injected object (_gccContentSender) into another (_gcc).
        // So we do it manually.
        _gcc.inject_(_trl, _lacl, _ps, _oel, _ds, _rpc, _pvc, _nvc, _emc, _gccContentSender, _a2t,
                _sidx2sid, _sid2sidx, _cfgLocalUser, _tm, _rl);
    }
}
