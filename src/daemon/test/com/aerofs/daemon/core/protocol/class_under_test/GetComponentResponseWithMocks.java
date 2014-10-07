/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.class_under_test;

import com.aerofs.daemon.core.Hasher;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.alias.Aliasing;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.migration.IEmigrantDetector;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.object.BranchDeleter;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.protocol.ComputeHash;
import com.aerofs.daemon.core.protocol.ContentUpdater;
import com.aerofs.daemon.core.protocol.GetComponentResponse;
import com.aerofs.daemon.core.protocol.MetaDiff;
import com.aerofs.daemon.core.protocol.MetaUpdater;
import com.aerofs.daemon.core.protocol.ReceiveAndApplyUpdate;
import com.aerofs.daemon.core.store.StoreCreator;

import static org.mockito.Mockito.mock;

/**
 * This class contains a NewUpdates object with its supporting mock objects
 */
public class GetComponentResponseWithMocks extends AbstractClassUnderTestWithMocks
{
    // For GetComponentReply
    public final DirectoryService _ds = mock(DirectoryService.class);
    public final IncomingStreams _iss = mock(IncomingStreams.class);
    public final MetaDiff _mdiff = mock(MetaDiff.class);
    public final Aliasing _al = mock(Aliasing.class);
    public final MapAlias2Target _a2t = mock(MapAlias2Target.class);
    public final IEmigrantDetector _emd = mock(IEmigrantDetector.class);
    public final NativeVersionControl _nvc = mock(NativeVersionControl.class);
    public final ObjectCreator _oc = mock(ObjectCreator.class);
    public final ObjectMover _om = mock(ObjectMover.class);
    public final StoreCreator _sc = mock(StoreCreator.class);
    public final VersionUpdater _vu = mock(VersionUpdater.class);
    public final BranchDeleter _bd = mock(BranchDeleter.class);
    public final Hasher _hasher = mock(Hasher.class);
    public final ComputeHash _computeHash = mock(ComputeHash.class);
    public final ReceiveAndApplyUpdate _raau = mock(ReceiveAndApplyUpdate.class);

    public final MetaUpdater _mu = new MetaUpdater();
    public final ContentUpdater _cu =
            new ContentUpdater(_tm, _ds, _iss, _a2t, _lacl, _nvc, _bd, _hasher, _computeHash, _raau);
    public final GetComponentResponse _gcr =
            new GetComponentResponse(_mu, _cu, _iss);

    public GetComponentResponseWithMocks()
    {
        _mu.inject_(_tm, _ds, _nvc, _mdiff, _al, _a2t, _lacl, _emd, _oc, _om, _sc, _vu);
    }
}
