/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.status;

import com.aerofs.daemon.core.net.IDownloadStateListener.State;
import com.aerofs.daemon.core.net.IUploadStateListener.Value;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.Server;
import com.aerofs.daemon.core.syncstatus.LocalSyncStatus;
import com.aerofs.daemon.core.syncstatus.SyncStatusSummary;
import com.aerofs.daemon.lib.db.UserAndDeviceNames;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.ex.ExExpelled;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * This class aggregates different status indicators:
 *   - sync status (see {@link LocalSyncStatus}
 *   - transfer state (see {@link TransferStateAggregator}
 *   - server status (see {@link ServerConnectionStatus}
 * and condenses them into a single status suitable for icon overlays and similar user-friendly
 * information displays.
 */
public class PathStatus
{
    private final LocalSyncStatus _lsync;
    private final TransferStateAggregator _tsa;
    private final ServerConnectionStatus _scs;
    private final UserAndDeviceNames _udn;
    private final CfgLocalUser _user;

    @Inject
    public PathStatus(LocalSyncStatus lsync, TransferStateAggregator tsa,
            ServerConnectionStatus scs, UserAndDeviceNames udn, CfgLocalUser user)
    {
        _lsync = lsync;
        _tsa = tsa;
        _scs = scs;
        _udn = udn;
        _user = user;
    }

    public PBPathStatus getStatus_(Path path)
    {
        return PBPathStatus.newBuilder()
                .setSync(getSyncStatus_(path))
                .setFlags(_tsa.state_(path))
                .build();
    }

    /**
     * Update aggregated upload state, derive required upload/download notifications and
     * merge them with sync status
     */
    public Map<Path, PBPathStatus> uploadNotifications_(SOCID socid, Value value)
    {
        // Only care about content transfer
        // NOTE: this also ensure that the object is not expelled
        if (socid.cid().isMeta()) return Maps.newHashMap();

        return transferNotifications_(_tsa.upload_(socid, value));
    }

    /**
     * Update aggregated download state, derive required upload/download notifications and
     * merge them with sync status
     */
    public Map<Path, PBPathStatus> downloadNotifications_(SOCID socid, State state)
    {
        // Only care about content transfer
        // NOTE: this also ensure that the object is not expelled
        if (socid.cid().isMeta()) return Maps.newHashMap();

        return transferNotifications_(_tsa.download_(socid, state));
    }

    private Map<Path, PBPathStatus> transferNotifications_(Map<Path, Integer> transferNotifications)
    {
        Map<Path, PBPathStatus> notifications = Maps.newHashMap();
        for (Entry<Path, Integer> e : transferNotifications.entrySet()) {
            Path path = e.getKey();
            notifications.put(path, PBPathStatus.newBuilder()
                    .setSync(getSyncStatus_(path))
                    .setFlags(e.getValue())
                    .build());
        }
        return notifications;
    }

    /**
     * Merge sync status notifications with upload/download state
     */
    public Map<Path, PBPathStatus> syncStatusNotifications_(Set<Path> syncStatusNotifications)
    {
        Map<Path, PBPathStatus> notifications = Maps.newHashMap();
        for (Path path : syncStatusNotifications) {
            notifications.put(path, getStatus_(path));
        }
        return notifications;
    }

    /**
     * @return server-status-aware protobuf-encoded sync status summary
     */
    private PBPathStatus.Sync getSyncStatus_(Path path) {
        return _scs.isConnected(Server.VERKEHR, Server.SYNCSTAT)
                ? getSyncStatusSummary_(path)
                : PBPathStatus.Sync.UNKNOWN;
    }

    /**
     * @return protobuf-encoded sync status summary
     */
    private PBPathStatus.Sync getSyncStatusSummary_(Path path)
    {
        SyncStatusSummary s = new SyncStatusSummary(_udn, _user);
        try {
            _lsync.aggregateAcrossStores_(path, s);
        } catch (SQLException e) {
            throw Util.fatal(e);
        } catch (ExExpelled e) {
            throw Util.fatal(e);
        } catch (ExNotFound e) {
            throw Util.fatal(e);
        }
        if (s.allInSync) return PBPathStatus.Sync.IN_SYNC;
        if (s.atLeastOneInSync) return PBPathStatus.Sync.PARTIAL_SYNC;
        return PBPathStatus.Sync.OUT_SYNC;
    }
}
