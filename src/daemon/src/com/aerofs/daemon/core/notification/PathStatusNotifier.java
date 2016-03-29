/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.notification.ConflictNotifier.IConflictStateListener;
import com.aerofs.daemon.core.status.ISyncStatusPropagator;
import com.aerofs.daemon.core.status.PathFlagAggregator;
import com.aerofs.daemon.core.status.PathStatus;
import com.aerofs.daemon.core.status.SyncStatusOnline;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferProgress;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferredItem;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgSyncStatusEnabled;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.aerofs.proto.PathStatus.PBPathStatus.Sync;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;

import static com.aerofs.daemon.core.notification.Notifications.newConflictCountNotification;
import static com.aerofs.daemon.core.notification.Notifications.newPathStatusNotification;

/**
 * Listens to upload, download and sync status changes and emits the appropriate
 * merged status notifications through the RitualNotificationServer.
 *
 * See {@link PathStatus}
 */
public class PathStatusNotifier implements IConflictStateListener, ISnapshotableNotificationEmitter
{
    private static final Logger l = Loggers.getLogger(PathStatusNotifier.class);

    private final PathStatus _ps;
    private final DirectoryService _ds;
    private final RitualNotificationServer _rns;

    @Inject
    public PathStatusNotifier(RitualNotificationServer rns, DirectoryService ds, PathStatus ps,
            ISyncStatusPropagator ssp, SyncStatusOnline sso, CfgSyncStatusEnabled sse, DownloadState dls,
            UploadState uls) {
        _ps = ps;
        _ds = ds;
        _rns = rns;

        if (sse.get()) {
            ssp.addListener((updates) -> {
                Map<Path, PBPathStatus> pathStatuses = Maps.newHashMapWithExpectedSize(updates.size());
                for (Entry<Path, Sync> update : updates.entrySet()) {
                    try {
                        pathStatuses.put(update.getKey(), _ps.getStatus_(update.getKey(), update.getValue()));
                    } catch (Exception e) {
                        // Swallow exception to minimize front-end inaccuracy
                        l.warn("error sending notification", e);
                    }
                }
                sendPathStatusNotification_(pathStatuses);
            });
        }

        // TODO re-add this after shellexts handle it correctly
        // sso.addListener((online) -> _rns.getRitualNotifier()
        // .sendNotification(Notifications.newPathStatusOutOfDateNotification()));

        uls.addListener_(
                (item, progress) -> onStateChanged_(item, progress, PathFlagAggregator.Uploading));
        dls.addListener_(
                (item, progress) -> onStateChanged_(item, progress, PathFlagAggregator.Downloading));
    }

    void sendPathStatusNotification_(Map<Path, PBPathStatus> pathStatuses) {
        if (l.isTraceEnabled()) {
            l.trace("Sending path status notification");
            for (Entry<Path, PBPathStatus> entry : pathStatuses.entrySet()) {
                l.trace("Path: {}, Sync: {}", entry.getKey().toString(), entry.getValue().getSync());
            }
        }
        _rns.getRitualNotifier().sendNotification(newPathStatusNotification(pathStatuses));
    }

    private void onStateChanged_(TransferredItem key, TransferProgress value, int direction) {
        SOCID socid = key._socid;

        // Only care about content transfer
        // NOTE: this also ensures that the object is not expelled
        if (socid.cid().isMeta()) return;

        try {
            Path path = _ds.resolveNullable_(socid.soid());
            sendPathStatusNotification_(_ps.setTransferState_(socid, path, value, direction));
        } catch (SQLException e) {
            /**
             * We bury exceptions to comply with ITransferStateListener
             * interface This is safe because upper layers can deal with a
             * temporary inconsistency
             */
            l.warn("", e);
        }
    }

    void sendConflictCountNotification_() {
        _rns.getRitualNotifier().sendNotification(newConflictCountNotification(_ps.conflictCount_()));
    }

    @Override
    public void branchesChanged_(Map<Path, Boolean> conflicts) {
        try {
            sendPathStatusNotification_(_ps.setConflictState_(conflicts));
            sendConflictCountNotification_();
        } catch (SQLException e) {
            l.warn("error sending conflict notifications", e);
        }
    }

    @Override
    public final void sendSnapshot_() {
        sendConflictCountNotification_();
    }
}
