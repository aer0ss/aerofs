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
import java.util.Map;
import java.util.Map.Entry;

/**
 * The "path status" is a compact status indicator at path (file/directory) granularity, suitable
 * for icon overlay and similar displays.
 */
public class PathStatus
{
    private final PathFlagAggregator _tsa;

    @Inject
    public PathStatus(PathFlagAggregator tsa)
    {
        _tsa = tsa;
    }

    public PBPathStatus getStatus_(Path path)
    {
        // TODO remove the Sync value here, once we rebuild the shell ext.
        return PBPathStatus.newBuilder()
                .setSync(Sync.UNKNOWN)
                .setFlags(_tsa.state_(path))
                .build();
    }

    /**
     * Update aggregated upload state
     * @return paths whose status was affected and their new aggregated status
     */
    public Map<Path, PBPathStatus> setTransferState_(SOCID socid, @Nullable Path path,
            TransferProgress value, int direction)
    {
        assert !socid.cid().isMeta();
        return notificationsForFlagChanges_(
                _tsa.changeFlagsOnTransferNotification_(socid, path, value, direction));
    }

    /**
     * @return path whose status was affected and their new "path status"
     */
    private Map<Path, PBPathStatus> notificationsForFlagChanges_(Map<Path, Integer> flagChanges)
    {
        Map<Path, PBPathStatus> notifications = Maps.newHashMap();

        for (Entry<Path, Integer> e : flagChanges.entrySet()) {
            Path path = e.getKey();

            // TODO remove the Sync value here, once we rebuild the shell ext.
            notifications.put(path, PBPathStatus.newBuilder()
                    .setSync(Sync.UNKNOWN)
                    .setFlags(e.getValue())
                    .build());
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
}
