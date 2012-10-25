/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.IDownloadStateListener;
import com.aerofs.daemon.core.net.IUploadStateListener;
import com.aerofs.daemon.core.status.PathStatus;
import com.aerofs.daemon.core.syncstatus.AggregateSyncStatus.IListener;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.proto.RitualNotifications.PBPathStatusEvent;
import com.google.common.collect.Maps;
import org.apache.log4j.Logger;

import java.sql.SQLException;
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
    private static final Logger l = Util.l(PathStatusNotifier.class);

    private final PathStatus _so;
    private final DirectoryService _ds;
    private final RitualNotificationServer _notifier;

    public PathStatusNotifier(RitualNotificationServer notifier, DirectoryService ds, PathStatus so)
    {
        _so = so;
        _ds = ds;
        _notifier = notifier;
    }

    @Override
    public void syncStatusChanged_(Set<Path> changes)
    {
        notify_(_so.syncStatusNotifications_(changes));
    }

    @Override
    public void stateChanged_(SOCID socid, State state)
    {
        // Only care about content transfer
        // NOTE: this also ensure that the object is not expelled
        if (socid.cid().isMeta()) return;
        try {
            notify_(_so.downloadNotifications_(socid, _ds.resolveNullable_(socid.soid()), state));
        } catch (SQLException e) {
            /**
             * We bury exceptions to comply with IDownloadStateListener interface
             * TODO: send SV defect?
             */
            l.warn(Util.e(e));
        }
    }

    @Override
    public void stateChanged_(Key key, Value value)
    {
        SOCID socid = key._socid;
        // Only care about content transfer
        // NOTE: this also ensure that the object is not expelled
        if (socid.cid().isMeta()) return;
        try {
            notify_(_so.uploadNotifications_(socid, _ds.resolveNullable_(socid.soid()), value));
        } catch (SQLException e) {
            /**
             * We bury exceptions to comply with IUploadStateListener interface
             * TODO: send SV defect?
             */
            l.warn(Util.e(e));
        }
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
