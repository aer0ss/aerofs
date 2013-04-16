/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.UploadState;
import com.aerofs.daemon.core.protocol.DownloadState;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.IServiceStatusListener;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.Server;
import com.aerofs.daemon.core.status.PathStatus;
import com.aerofs.daemon.core.syncstatus.AggregateSyncStatus;
import com.aerofs.daemon.core.syncstatus.SyncStatusSynchronizer;
import com.aerofs.daemon.core.syncstatus.SyncStatusSynchronizer.IListener;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SIndex;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.Set;

/**
 * Break ugly dependency cycles by moving all business logic wirings outside the server class
 */
public class RitualNotificationWirings implements RitualNotificationServer.IConnectionListener
{
    private static final Logger l = Loggers.getLogger(RitualNotificationWirings.class);

    private final RitualNotificationServer _rns;

    private final CoreQueue _cq;
    private final CoreScheduler _sched;
    private final DownloadState _dls;
    private final UploadState _uls;
    private final PathStatusNotifier _psn;
    private final PathStatus _so;
    private final SyncStatusSynchronizer _sss;
    private final AggregateSyncStatus _agss;
    private final DirectoryService _ds;
    private final ServerConnectionStatus _scs;
    private final ConflictState _cl;
    private final UserAndDeviceNames _nr;

    @Inject
    public RitualNotificationWirings(RitualNotificationServer rns, CoreQueue cq,
            CoreScheduler sched, DirectoryService ds,  DownloadState dls, UploadState uls,
            PathStatus so, ServerConnectionStatus scs, SyncStatusSynchronizer sss,
            AggregateSyncStatus agss, ConflictState cl, UserAndDeviceNames nr)
    {
        _rns = rns;

        _cq = cq;
        _sched = sched;
        _dls = dls;
        _uls = uls;
        _so = so;
        _sss = sss;
        _agss = agss;
        _ds = ds;
        _scs = scs;
        _cl = cl;
        _nr = nr;
        _psn = new PathStatusNotifier(_rns, _ds, _so);
    }

    public void init_() throws IOException
    {
        _rns.addListener_(this);

        _dls.addListener_(new DownloadStateListener(_rns, _ds, _nr));
        _uls.addListener_(new UploadStateListener(_rns, _ds, _nr));

        // Merged status notifier listens on all input sources
        final PathStatusNotifier sn = new PathStatusNotifier(_rns, _ds, _so);
        _dls.addListener_(sn);
        _uls.addListener_(sn);
        _agss.addListener_(sn);
        _cl.addListener_(sn);

        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                try {
                    _cl.sendSnapshot_(sn);
                } catch (SQLException e) {
                    l.warn("Failed to send conflict snapshot", e);
                }
            }
        }, 0);

        // detect apparition of new device in a store to send CLEAR_CACHE message
        _sss.addListener_(new IListener() {
            @Override
            public void devicesChanged(Set<SIndex> stores)
            {
                _rns.clearStatusCache_();
            }
        });

        // detect change of server availability to send CLEAR_CACHE message
        _scs.addListener(new IServiceStatusListener() {
            @Override
            public boolean isAvailable(ImmutableMap<Server, Boolean> status)
            {
                return status.get(Server.VERKEHR) && status.get(Server.SYNCSTAT);
            }

            @Override
            public void available() {
                l.info("sss available");
                // sync status back up: must clear cache in shellext to avoid false negatives
                // this callback is never called with the core lock held, must schedule...
                scheduleClearStatusCache();
            }

            @Override
            public void unavailable()
            {
                l.info("sss unavailable");
                // sync status down: must clear cache in shellext to avoid showing outdated status
                // this callback is never called with the core lock held, must schedule...
                scheduleClearStatusCache();
            }
        }, Server.VERKEHR, Server.SYNCSTAT);

        SPBlockingClient.setListener(new DaemonBadCredentialListener(_rns));
    }

    private void scheduleClearStatusCache()
    {
        // this is kinda retarted but due to the snapshot logic notifications can only be sent from
        // a core thread, even though other synchronization is used
        // TODO: investigate a refactoring that removes this requirement
        _sched.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                _rns.clearStatusCache_();
            }
        }, 0);
    }

    @Override
    public void onIncomingconnection(InetSocketAddress from)
    {
        // must enqueue since this method is called by non-core threads
        _cq.enqueueBlocking(new EISendSnapshot(_rns, from, _dls, _uls, _psn, _ds, _nr), Prio.HI);
    }
}
