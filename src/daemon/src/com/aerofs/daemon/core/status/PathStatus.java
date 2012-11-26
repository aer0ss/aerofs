/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.status;

import com.aerofs.daemon.core.protocol.IDownloadStateListener.State;
import com.aerofs.daemon.core.net.IUploadStateListener.Value;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.Server;
import com.aerofs.daemon.core.syncstatus.LocalSyncStatus;
import com.aerofs.daemon.core.syncstatus.SyncStatusSummary;
import com.aerofs.daemon.lib.db.UserAndDeviceNames;
import com.aerofs.lib.Path;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.ex.ExExpelled;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.aerofs.proto.PathStatus.PBPathStatus.Sync;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * The "path status" is a compact status indicator at path (file/directory) granularity, suitable
 * for icon overlay and similar displays. It is obtained by aggregating the following indicators:
 *   - sync status (see {@link LocalSyncStatus}
 *   - transfer state (see {@link PathFlagAggregator}
 *   - server status (see {@link ServerConnectionStatus}
 */
public class PathStatus
{
    private final LocalSyncStatus _lsync;
    private final PathFlagAggregator _tsa;
    private final ServerConnectionStatus _scs;
    private final UserAndDeviceNames _udn;
    private final CfgLocalUser _user;

    @Inject
    public PathStatus(LocalSyncStatus lsync, PathFlagAggregator tsa,
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
     * Update aggregated upload state
     * @return paths whose status was affected and their new aggregated status
     */
    public Map<Path, PBPathStatus> setUploadState_(SOCID socid, @Nullable Path path, Value value)
    {
        assert !socid.cid().isMeta();
        return notificationsForFlagChanges_(
                _tsa.changeFlagsOnUploadNotification_(socid, path, value));
    }

    /**
     * Update aggregated download state
     * @return paths whose status was affected and their new aggregated status
     */
    public Map<Path, PBPathStatus> setDownloadState_(SOCID socid, @Nullable Path path, State state)
    {
        assert !socid.cid().isMeta();
        return notificationsForFlagChanges_(
                _tsa.changeFlagsOnDownloadNotification_(socid, path, state));
    }

    /**
     * @return path whose status was affected and their new "path status"
     */
    private Map<Path, PBPathStatus> notificationsForFlagChanges_(Map<Path, Integer> flagChanges)
    {
        Map<Path, PBPathStatus> notifications = Maps.newHashMap();
        for (Entry<Path, Integer> e : flagChanges.entrySet()) {
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
    public Map<Path, PBPathStatus> notificationsForSyncStatusChanges_(Set<Path> syncStatusChanges)
    {
        Map<Path, PBPathStatus> notifications = Maps.newHashMap();
        for (Path path : syncStatusChanges) {
            notifications.put(path, getStatus_(path));
        }
        return notifications;
    }

    /**
     * Update aggregated status flags
     * @return paths whose status was affected and their new aggregated status
     */
    public Map<Path, PBPathStatus> setConflictState_(Map<Path, Boolean> conflictChanges)
    {
        Map<Path, Integer> flagChanges = Maps.newHashMap();
        for (Entry<Path, Boolean> e : conflictChanges.entrySet()) {
            _tsa.changeFlagsOnConflictNotification_(e.getKey(), e.getValue(), flagChanges);
        }
        return notificationsForFlagChanges_(flagChanges);
    }

    public int conflictCount_()
    {
        return _tsa.nodesWithFlag_(PathFlagAggregator.Conflict);
    }

    /**
     * @return server-status-aware protobuf-encoded sync status summary
     */
    private Sync getSyncStatus_(Path path) {
        return _scs.isConnected(Server.VERKEHR, Server.SYNCSTAT)
                ? getSyncStatusSummary_(path)
                : Sync.UNKNOWN;
    }

    /**
     * @return protobuf-encoded sync status summary
     */
    private Sync getSyncStatusSummary_(Path path)
    {
        SyncStatusSummary s = new SyncStatusSummary(_udn, _user);
        try {
            _lsync.aggregateAcrossStores_(path, s);
        } catch (SQLException e) {
            throw SystemUtil.fatalWithReturn(e);
        } catch (ExExpelled e) {
            return Sync.OUT_SYNC;
        } catch (ExNotFound e) {
            // Always return out of sync for unknown objects to avoid race conditions between the
            // GUI and the daemon.
            return Sync.OUT_SYNC;
        }
        // NOTE: files only present on the local device are considered out of sync
        if (!s.isPartiallySynced()) return Sync.OUT_SYNC;
        if (s.allInSync()) return Sync.IN_SYNC;
        return Sync.PARTIAL_SYNC;
    }
}
