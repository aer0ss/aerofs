/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.online_status.OnlineStatusNotifier;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.ritual_notification.IRitualNotificationClientConnectedListener;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Set;

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
    private final PathStatusNotifier _psn;
    private final ConflictNotifier _cl;
    private final DownloadNotifier _dn;
    private final UploadNotifier _un;
    private final OnlineStatusNotifier _osn;
    private final Set<ISnapshotableNotificationEmitter> _snapshotables;

    @Inject
    public NotificationService(CoreScheduler sched, RitualNotificationServer rns,
            DownloadState dls, DownloadNotifier dn, UploadState uls, UploadNotifier un,
            ConflictNotifier cl,
            PathStatusNotifier psn, OnlineStatusNotifier osn,
            Set<ISnapshotableNotificationEmitter> snapshotables)
    {
        _sched = sched;
        _rns = rns;
        _dls = dls;
        _uls = uls;
        _psn = psn;
        _cl = cl;
        _dn = dn;
        _un = un;
        _osn = osn;
        _snapshotables = snapshotables;
    }

    //
    // notifier_s_ setup
    //

    private boolean filterMeta_()
    {
        return Cfg.useTransferFilter();
    }

    private void setupRitualNotificationListener_()
    {
        _rns.addListener(this);
    }

    private void setupTransferNotifiers_()
    {
        _dn.filterMeta_(filterMeta_());
        _dls.addListener_(_dn);

        _un.filterMeta_(filterMeta_());
        _uls.addListener_(_un);
    }

    private void setupPathStatusNotifiers_()
    {
        // merged status notifier listens on all input sources
        _cl.addListener_(_psn);

        _sched.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                try {
                    // initialize PathStatus based on current conflict state
                    _cl.sendSnapshot_(_psn);
                } catch (SQLException e) {
                    l.warn("Failed to send conflict snapshot", e);
                }
            }
        }, 0);
    }

    private void setupOnlineStatusNotifier()
    {
        _osn.init_();
    }

    public void init_()
    {
        setupRitualNotificationListener_();
        setupTransferNotifiers_();
        setupPathStatusNotifiers_();
        setupOnlineStatusNotifier();
    }

    //
    // ritual-notification connection listener
    //

    @Override
    public void onNotificationClientConnected()
    {
        _sched.schedule(new EISendSnapshot(_dls, _dn, _uls, _un, _snapshotables, filterMeta_()), 0);
    }
}
