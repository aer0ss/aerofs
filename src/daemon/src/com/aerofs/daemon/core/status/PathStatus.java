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
 *   - transfer state (see {@link TransferStateAggregator}
 *   - server status (see {@link ServerConnectionStatus}
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
     * Update aggregated upload state
     * @return paths whose status was affected and their new aggregated transfer state
     */
    public Map<Path, PBPathStatus> uploadNotifications_(SOCID socid, @Nullable Path path,
            Value value)
    {
        assert !socid.cid().isMeta();
        return transferNotifications_(_tsa.upload_(socid, path, value));
    }

    /**
     * Update aggregated download state
     * @return paths whose status was affected and their new aggregated transfer state
     */
    public Map<Path, PBPathStatus> downloadNotifications_(SOCID socid, @Nullable Path path,
            State state)
    {
        assert !socid.cid().isMeta();
        return transferNotifications_(_tsa.download_(socid, path, state));
    }

    /**
     * @return path whose status was affected and their new "path status"
     */
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
            throw SystemUtil.fatal(e);
        } catch (ExExpelled e) {
            return Sync.OUT_SYNC;
        } catch (ExNotFound e) {
            return Sync.UNKNOWN;
        }
        // NOTE: files only present on the local device are considered out of sync
        if (!s.atLeastOneInSync) return Sync.OUT_SYNC;
        if (s.allInSync) return Sync.IN_SYNC;
        return Sync.PARTIAL_SYNC;
    }
}
