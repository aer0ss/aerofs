/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.class_under_test;

import com.aerofs.base.analytics.Analytics;
import com.aerofs.daemon.core.CoreScheduler;
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
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.protocol.*;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.id.SOKID;

import java.sql.SQLException;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class contains a NewUpdates object with its supporting mock objects
 */
public class GetComponentResponseWithMocks extends AbstractClassUnderTestWithMocks
{
    // For GetComponentReply
    public final DirectoryService _ds = mock(DirectoryService.class);
    public final IPhysicalStorage _ps = mock(IPhysicalStorage.class);
    public final IncomingStreams _iss = mock(IncomingStreams.class);
    public final MetaDiff _mdiff = mock(MetaDiff.class);
    public final Aliasing _al = mock(Aliasing.class);
    public final MapAlias2Target _a2t = mock(MapAlias2Target.class);
    public final IEmigrantDetector _emd = mock(IEmigrantDetector.class);
    public final NativeVersionControl _nvc = mock(NativeVersionControl.class);
    public final ObjectCreator _oc = mock(ObjectCreator.class);
    public final ObjectMover _om = mock(ObjectMover.class);
    public final VersionUpdater _vu = mock(VersionUpdater.class);
    public final BranchDeleter _bd = mock(BranchDeleter.class);
    public final Hasher _hasher = mock(Hasher.class);
    public final ComputeHash _computeHash = mock(ComputeHash.class);
    public final CentralVersionDatabase _cvdb = mock(CentralVersionDatabase.class);
    public final ContentChangesDatabase _ccdb = mock(ContentChangesDatabase.class);
    public final RemoteContentDatabase _rcdb = mock(RemoteContentDatabase.class);
    private final PrefixVersionControl _pvc = mock(PrefixVersionControl.class);
    private final CoreScheduler _sched = mock(CoreScheduler.class);
    private final ContentReceiver _rc = new ContentReceiver(_pvc, _ps, mock(DownloadState.class),
            _iss, _tm, _sched, mock(ProgressIndicators.class));
    private final ContentProvider _provider = new DaemonContentProvider(_ds, _ps, _ccdb, mock(Analytics.class));

    public final LegacyCausality _legacyCausality = new LegacyCausality(_ds, _nvc, _bd);
    public final MetaUpdater _mu = new MetaUpdater();
    public final ContentUpdater _cu =
            new ContentUpdater(_tm, _provider, _ps, _rc, _legacyCausality, _ds, _pvc);
    public final GetComponentResponse _gcr =
            new GetComponentResponse(_mu, _cu, _iss, _lacl, _a2t, _legacyCausality, _hasher, _computeHash);

    public GetComponentResponseWithMocks()
    {
        _mu.inject_(_tm, _ds, _nvc, _mdiff, _al, _a2t, _lacl, _emd, _oc, _om, _vu);
        try {
            when(_ps.newPrefix_(any(SOKID.class), anyString())).thenReturn(mock(IPhysicalPrefix.class));
        } catch (SQLException e) { fail(); }
    }
}
