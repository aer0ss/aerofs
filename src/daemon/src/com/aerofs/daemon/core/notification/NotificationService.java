/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.notification.DownloadNotifier.DownloadThrottler;
import com.aerofs.daemon.core.notification.UploadNotifier.UploadThrottler;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.IServiceStatusListener;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.Server;
import com.aerofs.daemon.core.status.PathStatus;
import com.aerofs.daemon.core.syncstatus.AggregateSyncStatus;
import com.aerofs.daemon.core.syncstatus.SyncStatusSynchronizer;
import com.aerofs.daemon.core.syncstatus.SyncStatusSynchronizer.IListener;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.SIndex;
import com.aerofs.ritual_notification.IRitualNotificationClientConnectedListener;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

import static com.aerofs.daemon.core.notification.Notifications.newPathStatusOutOfDateNotification;
import static com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.Server.SYNCSTAT;
import static com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.Server.VERKEHR;

/**
 * Break ugly dependency cycles by moving all business logic wirings outside the server class
 */
public class NotificationService implements IRitualNotificationClientConnectedListener
{
    private static final Logger l = Loggers.getLogger(NotificationService.class);

    private final CoreScheduler _sched;
    private final RitualNotificationServer _rns;
    private final DownloadState _dls;
    private final UploadState _uls;
    private final BadCredentialNotifier _bcl;
    private final PathStatusNotifier _psn;
    private final SyncStatusSynchronizer _sss;
    private final AggregateSyncStatus _agss;
    private final ServerConnectionStatus _scs;
    private final ConflictNotifier _cl;
    private final DownloadNotifier _dn;
    private final UploadNotifier _un;

    @Inject
    public NotificationService(CoreScheduler sched, RitualNotificationServer rns,
            DirectoryService ds, UserAndDeviceNames nr, DownloadState dls, UploadState uls,
            BadCredentialNotifier bcl, PathStatus so, SyncStatusSynchronizer sss,
            AggregateSyncStatus agss, ServerConnectionStatus scs, ConflictNotifier cl,
            DownloadThrottler dlt, UploadThrottler ult)
    {
        _sched = sched;
        _rns = rns;
        _dls = dls;
        _uls = uls;
        _bcl = bcl;
        _psn = new PathStatusNotifier(_rns, ds, so, _dls, _uls);
        _sss = sss;
        _agss = agss;
        _scs = scs;
        _cl = cl;
        _dn = new DownloadNotifier(ds, nr, _rns, dlt);
        _un = new UploadNotifier(ds, nr, _rns, ult);
    }

    //
    // notifier_s_ setup
    //

    private void setupBadCredentialNotifier_()
    {
        SPBlockingClient.setBadCredentialListener(_bcl);
    }

    private boolean filterMeta_()
    {
        return Cfg.useTransferFilter();
    }

    private void setupTransferNotifiers_()
    {
        _dn.filterMeta_(filterMeta_());
        _dls.addListener_(_dn);

        _un.filterMeta_(filterMeta_());
        _uls.addListener_(_un);
    }

    private void pathStatusOutOfDate_()
    {
        _rns.getRitualNotifier().sendNotification(newPathStatusOutOfDateNotification());
    }

    private void setupPathStatusNotifiers_()
    {
        // merged status notifier listens on all input sources
        _agss.addListener_(_psn);
        _cl.addListener_(_psn);

        _sched.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                try {
                    _cl.sendSnapshot_(_psn); // FIXME (AG): ???
                } catch (SQLException e) {
                    l.warn("Failed to send conflict snapshot", e);
                }
            }
        }, 0);

        // detect when the set of devices syncing stores we care about change
        _sss.addListener_(new IListener()
        {
            @Override
            public void devicesChanged(Set<SIndex> stores)
            {
                pathStatusOutOfDate_();
            }
        });

        // detect when we may have gone offline/online
        _scs.addListener(new IServiceStatusListener()
        {
            @Override
            public boolean isAvailable(ImmutableMap<Server, Boolean> status)
            {
                return status.get(VERKEHR) && status.get(SYNCSTAT);
            }

            @Override
            public void available() {
                pathStatusOutOfDate_();
            }

            @Override
            public void unavailable()
            {
                pathStatusOutOfDate_();
            }
        }, VERKEHR, SYNCSTAT);
    }

    public void init_() throws IOException
    {
        setupBadCredentialNotifier_();
        setupTransferNotifiers_();
        setupPathStatusNotifiers_();
    }

    //
    // ritual-notification connection listener
    //

    @Override
    public void onNotificationClientConnected()
    {
        _sched.schedule(new EISendSnapshot(_dls, _dn, _uls, _un, _psn, filterMeta_()), 0);
    }
}
