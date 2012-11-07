/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.IDownloadStateListener;
import com.aerofs.daemon.core.net.IUploadStateListener;
import com.aerofs.daemon.core.notification.ConflictState.IConflictStateListener;
import com.aerofs.daemon.core.status.PathStatus;
import com.aerofs.daemon.core.syncstatus.AggregateSyncStatus.IListener;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.proto.RitualNotifications.PBPathStatusEvent;
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
public class PathStatusNotifier implements IListener, IDownloadStateListener, IUploadStateListener,
        IConflictStateListener
{
    private static final Logger l = Util.l(PathStatusNotifier.class);

    private final PathStatus _ps;
    private final DirectoryService _ds;
    private final RitualNotificationServer _notifier;

    public PathStatusNotifier(RitualNotificationServer notifier, DirectoryService ds, PathStatus ps)
    {
        _ps = ps;
        _ds = ds;
        _notifier = notifier;
    }

    @Override
    public void syncStatusChanged_(Set<Path> changes)
    {
        notify_(_ps.notificationsForSyncStatusChanges_(changes));
    }

    @Override
    public void stateChanged_(SOCID socid, State state)
    {
        // Only care about content transfer
        // NOTE: this also ensure that the object is not expelled
        if (socid.cid().isMeta()) return;
        try {
            notify_(_ps.setDownloadState_(socid, _ds.resolveNullable_(socid.soid()), state));
        } catch (SQLException e) {
            /**
             * We bury exceptions to comply with IDownloadStateListener interface
             * This is safe because upper layers can deal with a temporary inconsistency
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
            notify_(_ps.setUploadState_(socid, _ds.resolveNullable_(socid.soid()), value));
        } catch (SQLException e) {
            /**
             * We bury exceptions to comply with IUploadStateListener interface
             * This is safe because upper layers can deal with a temporary inconsistency
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

    // public for use in EISendSnapshot
    public void sendConflictCount_()
    {
        _notifier.sendEvent_(PBNotification.newBuilder()
                .setType(Type.CONFLICT_COUNT)
                .setConflictCount(_ps.conflictCount_())
                .build());
    }

    @Override
    public void branchesChanged_(Map<Path, Boolean> conflicts)
    {
        notify_(_ps.setConflictState_(conflicts));
    }
}
