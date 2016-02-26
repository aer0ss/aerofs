/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.status;

import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferProgress;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.aerofs.proto.PathStatus.PBPathStatus.Sync;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import javax.annotation.Nullable;

import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;

import static com.aerofs.proto.PathStatus.PBPathStatus.Sync.IN_SYNC;
import static com.aerofs.proto.PathStatus.PBPathStatus.Sync.UNKNOWN;

/**
 * The "path status" is a compact status indicator at path (file/directory) granularity, suitable
 * for icon overlay and similar displays.
 */
public class PathStatus
{
    private final PathFlagAggregator _tsa;
    private final ISyncStatusPropagator _syncStatusPropagator;

    @Inject
    public PathStatus(PathFlagAggregator tsa, ISyncStatusPropagator syncStatusPropagator)
    {
        _tsa = tsa;
        _syncStatusPropagator = syncStatusPropagator;
    }

    public PBPathStatus getStatus_(Path path) throws SQLException
    {
        return PBPathStatus.newBuilder()
                .setSync(_syncStatusPropagator.getSync_(path))
                .setFlags(_tsa.state_(path))
                .build();
    }

    public PBPathStatus getStatus_(Path path, Sync sync) throws SQLException {
        return PBPathStatus.newBuilder()
                .setSync(sync == IN_SYNC ? IN_SYNC : UNKNOWN)
                .setFlags(_tsa.state_(path))
                .build();
    }

    /**
     * Update aggregated upload state
     * @return paths whose status was affected and their new aggregated status
     * @throws SQLException
     */
    public Map<Path, PBPathStatus> setTransferState_(SOCID socid, @Nullable Path path,
            TransferProgress value, int direction) throws SQLException
    {
        assert !socid.cid().isMeta();
        return notificationsForFlagChanges_(
                _tsa.changeFlagsOnTransferNotification_(socid, path, value, direction));
    }

    /**
     * @return path whose status was affected and their new "path status"
     * @throws SQLException
     */
    private Map<Path, PBPathStatus> notificationsForFlagChanges_(Map<Path, Integer> flagChanges) throws SQLException
    {
        Map<Path, PBPathStatus> notifications = Maps.newHashMap();

        for (Entry<Path, Integer> e : flagChanges.entrySet()) {
            Path path = e.getKey();

            notifications.put(path, PBPathStatus.newBuilder()
                    .setSync(_syncStatusPropagator.getSync_(path))
                    .setFlags(e.getValue())
                    .build());
        }

        return notifications;
    }

    /**
     * Update aggregated status flags
     * @return paths whose status was affected and their new aggregated status
     * @throws SQLException
     */
    public Map<Path, PBPathStatus> setConflictState_(Map<Path, Boolean> conflictChanges) throws SQLException
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
}
