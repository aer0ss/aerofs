/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.download.DownloadState;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.ITransferStateListener;
import com.aerofs.daemon.core.net.ITransferStateListener.Key;
import com.aerofs.daemon.core.net.ITransferStateListener.Value;
import com.aerofs.daemon.core.net.UploadState;
import com.aerofs.daemon.core.notification.ConflictState.IConflictStateListener;
import com.aerofs.daemon.core.status.PathFlagAggregator;
import com.aerofs.daemon.core.status.PathStatus;
import com.aerofs.daemon.core.syncstatus.AggregateSyncStatus.IListener;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.proto.RitualNotifications.PBPathStatusEvent;
import org.slf4j.Logger;

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
public class PathStatusNotifier implements IListener, IConflictStateListener
{
    private static final Logger l = Loggers.getLogger(PathStatusNotifier.class);

    private final PathStatus _ps;
    private final DirectoryService _ds;
    private final RitualNotificationServer _notifier;

    public PathStatusNotifier(RitualNotificationServer notifier, DirectoryService ds, PathStatus ps,
            DownloadState dls, UploadState uls)
    {
        _ps = ps;
        _ds = ds;
        _notifier = notifier;

        uls.addListener_(new ITransferStateListener() {
            @Override
            public void stateChanged_(Key key, Value value)
            {
                onStateChanged_(key, value, PathFlagAggregator.Uploading);
            }
        });

        dls.addListener_(new ITransferStateListener() {
            @Override
            public void stateChanged_(Key key, Value value)
            {
                onStateChanged_(key, value, PathFlagAggregator.Downloading);
            }
        });
    }

    private void onStateChanged_(Key key, Value value, int direction)
    {
        SOCID socid = key._socid;
        // Only care about content transfer
        // NOTE: this also ensures that the object is not expelled
        if (socid.cid().isMeta()) return;

        try {
            Path path = _ds.resolveNullable_(socid.soid());
            notify_(_ps.setTransferState_(socid, path, value, direction));
        } catch (SQLException e) {
            /**
             * We bury exceptions to comply with ITransferStateListener interface
             * This is safe because upper layers can deal with a temporary inconsistency
             */
            l.warn(Util.e(e));
        }
    }

    @Override
    public void syncStatusChanged_(Set<Path> changes)
    {
        notify_(_ps.notificationsForSyncStatusChanges_(changes));
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
        sendConflictCount_();
    }
}
