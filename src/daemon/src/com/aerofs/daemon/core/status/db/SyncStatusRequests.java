package com.aerofs.daemon.core.status.db;

import com.aerofs.lib.id.SOID;

import java.util.HashMap;
import java.util.Map;

/**
 * Keep track of outstanding requests to polaris for current sync status
 */
public class SyncStatusRequests
{
    private Map<SOID, Long> requests = new HashMap<>();

    public synchronized void setSyncRequest(SOID soid, long version) {
        requests.compute(soid,
                (key, current) -> current == null || current < version ? version : current);
    }

    public synchronized boolean deleteSyncRequest(SOID soid) {
        return requests.remove(soid) != null;
    }

    public synchronized boolean deleteSyncRequestIfVersionMatches(SOID soid, long version) {
        return requests.remove(soid, version);
    }

    public synchronized Long getSyncRequestVersion(SOID soid) {
        return requests.get(soid);
    }
}
