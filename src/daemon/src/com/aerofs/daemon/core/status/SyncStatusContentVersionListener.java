package com.aerofs.daemon.core.status;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.IContentVersionListener;
import com.aerofs.daemon.core.net.DeviceToUserMapper;
import com.aerofs.daemon.core.polaris.api.LocationStatusBatch;
import com.aerofs.daemon.core.polaris.api.LocationStatusBatchResult;
import com.aerofs.daemon.core.polaris.api.LocationStatusObject;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase.RemoteContent;
import com.aerofs.daemon.core.status.db.SyncStatusRequests;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.OID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class SyncStatusContentVersionListener implements IContentVersionListener
{
    private final static Logger l = Loggers.getLogger(SyncStatusContentVersionListener.class);

    private final SyncStatusPropagator _syncStatusPropagator;
    private final SyncStatusRequests _syncStatusRequests;
    private final RemoteContentDatabase _rcdb;
    private final DeviceToUserMapper _d2u;
    private final SyncStatusBatchStatusChecker _syncStatusBatchStatusChecker;
    private final TransManager _tm;
    private final TransLocal<Map<SOID, Long>> _determineSyncStatusTransLocal;

    @Inject
    public SyncStatusContentVersionListener(SyncStatusPropagator syncStatusPropagator,
            SyncStatusRequests syncStatusRequests, RemoteContentDatabase rcdb, DeviceToUserMapper d2u,
            SyncStatusBatchStatusChecker syncStatusBatchStatusChecker, TransManager tm) {
        _syncStatusPropagator = syncStatusPropagator;
        _syncStatusRequests = syncStatusRequests;
        _rcdb = rcdb;
        _d2u = d2u;
        _syncStatusBatchStatusChecker = syncStatusBatchStatusChecker;
        _tm = tm;
        _determineSyncStatusTransLocal = new TransLocal<Map<SOID, Long>>() {
            @Override
            protected Map<SOID, Long> initialValue(Trans t) {
                Map<SOID, Long> versionsWithSyncStatusTBD = new ConcurrentHashMap<>();
                t.addListener_(new AbstractTransListener() {
                    @Override
                    public void committed_() {
                        determineSyncStatus_(versionsWithSyncStatusTBD);
                    }
                });
                return versionsWithSyncStatusTBD;
            }
        };
    }

    @Override
    public void onSetVersion_(SIndex sidx, OID oid, long v, Trans t) throws SQLException {
        SOID soid = new SOID(sidx, oid);
        l.trace("ENTER onSetVersion_: {} - {}", soid, v);

        RemoteContent max = _rcdb.getMaxRow_(sidx, oid);

        if (max == null) return;

        UserID user = _d2u.getUserIDForDIDNullable_(max.originator);
        if (user != null && user.isTeamServerID() && max.version == v) {
            l.trace("latest version from SA");
            _syncStatusPropagator.updateSyncStatus_(soid, true, t);
        } else {
            l.trace("not latest version from SA, setting synced=false");
            _syncStatusPropagator.updateSyncStatus_(soid, false, t);
            if (max.version == v) {
                l.trace("latest version, determining status");
                _determineSyncStatusTransLocal.get(t).put(soid, v);
            }
        }
    }

    protected void determineSyncStatus_(Map<SOID, Long> versionsWithSyncStatusTBD) {
        if (versionsWithSyncStatusTBD == null || versionsWithSyncStatusTBD.isEmpty()) return;

        LocationStatusBatch locationStatusBatch = buildLocationStatusBatch(versionsWithSyncStatusTBD);
        _syncStatusBatchStatusChecker.submitLocationStatusBatch(locationStatusBatch,
                new AsyncTaskCallback() {
                    @Override
                    public void onSuccess_(boolean hasMore) {
                        l.trace("Sync status determined for {} new version(s)",
                                versionsWithSyncStatusTBD.size());
                    }

                    /*
                     * Swallow any exceptions - since the file is marked as
                     * out-of-sync prior to the call to polaris, the worst that
                     * can happen as a result of an error here is that it's left
                     * out-of-sync temporarily until SyncStatusVerifier
                     * successfully asks polaris about it.
                     */
                    @Override
                    public void onFailure_(Throwable t) {
                        l.warn("Error determining sync status for {} updated file(s)",
                                versionsWithSyncStatusTBD.size());
                    }
                }, batchResult -> updateSyncStatusBatch_(versionsWithSyncStatusTBD, batchResult));
    }

    private LocationStatusBatch buildLocationStatusBatch(Map<SOID, Long> versionsWithSyncStatusTBD) {
        Collection<LocationStatusObject> operations = new ArrayList<>(
                versionsWithSyncStatusTBD.size());
        for (Entry<SOID, Long> versionWithSyncStatusTBD : versionsWithSyncStatusTBD.entrySet()) {
            operations.add(new LocationStatusObject(
                    versionWithSyncStatusTBD.getKey().oid().toStringFormal(),
                    versionWithSyncStatusTBD.getValue()));
            _syncStatusRequests.setSyncRequest(versionWithSyncStatusTBD.getKey(),
                    versionWithSyncStatusTBD.getValue());
        }
        return new LocationStatusBatch(operations);
    }

    protected Boolean updateSyncStatusBatch_(Map<SOID, Long> versionsWithSyncStatusTBD,
            LocationStatusBatchResult batchResult) throws SQLException {
        l.trace("enter updateSyncStatusBatch_");
        try (Trans t = _tm.begin_()) {
            Iterator<Entry<SOID, Long>> opsIterator = versionsWithSyncStatusTBD.entrySet().iterator();
            for (boolean backedUp : batchResult.results) {
                Entry<SOID, Long> op = opsIterator.next();
                SOID soid = op.getKey();
                if (_syncStatusRequests.deleteSyncRequestIfVersionMatches(soid, op.getValue())) {
                    l.trace("found matching request, updating sync status");
                    _syncStatusPropagator.updateSyncStatus_(soid, backedUp, t);
                } else {
                    l.trace("did not find matching request, skipping sync status update");
                }
            }
            t.commit_();
        }
        l.trace("leave updateSyncStatusBatch_");
        return false;
    }
}
