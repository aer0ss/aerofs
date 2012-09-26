/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.daemon.core.net.IDownloadStateListener;
import com.aerofs.daemon.core.net.IUploadStateListener;
import com.aerofs.daemon.core.status.PathStatus;
import com.aerofs.daemon.core.syncstatus.AggregateSyncStatus.IListener;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.proto.RitualNotifications.PBPathStatusEvent;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Listens to upload, download and sync status changes and emits the appropriate merged status
 * notifications through the RitualNotificationServer.
 *
 * See {@link PathStatus}
 */
public class PathStatusNotifier implements IListener, IDownloadStateListener, IUploadStateListener
{
    private final PathStatus _so;
    private final RitualNotificationServer _notifier;

    public PathStatusNotifier(RitualNotificationServer notifier, PathStatus so)
    {
        _so = so;
        _notifier = notifier;
    }

    @Override
    public void syncStatusChanged_(Set<Path> changes)
    {
        notify_(_so.syncStatusNotifications_(changes));
    }

    @Override
    public void stateChanged_(SOCID socid, State newState)
    {
        notify_(_so.downloadNotifications_(socid, newState));
    }

    @Override
    public void stateChanged_(Key key, Value value)
    {
        notify_(_so.uploadNotifications_(key._socid, value));
    }

    private void notify_(Map<Path, PBPathStatus> notifications)
    {
        PBPathStatusEvent.Builder bd = PBPathStatusEvent.newBuilder();
        for (Entry<Path, PBPathStatus> e : notifications.entrySet()) {
            bd.addPath(e.getKey().toPB());
            bd.addStatus(e.getValue());
        }
        _notifier.sendEvent_(PBNotification.newBuilder()
                .setType(Type.PATH_STATUS)
                .setPathStatus(bd)
                .build());
    }
}
