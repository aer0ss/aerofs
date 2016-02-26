package com.aerofs.daemon.core.status;

import com.aerofs.lib.Path;
import com.aerofs.proto.PathStatus.PBPathStatus.Sync;

import java.util.Map;

public interface ISyncStatusListener
{
    /**
     * Called by {@link SyncStatusPropagator} when the sync status of object(s)
     * changes.
     *
     * @param statusChanges
     *            objects whose status has changed
     */
    void onStatusChanged_(Map<Path, Sync> changedObjects);
}
