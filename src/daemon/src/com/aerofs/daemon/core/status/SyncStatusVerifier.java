package com.aerofs.daemon.core.status;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.IDirectoryServiceListener;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.polaris.api.LocationStatusBatch;
import com.aerofs.daemon.core.polaris.api.LocationStatusBatchResult;
import com.aerofs.daemon.core.polaris.api.LocationStatusObject;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.fetch.IShareListener;
import com.aerofs.daemon.core.status.db.OutOfSyncFilesDatabase;
import com.aerofs.daemon.core.status.db.OutOfSyncFilesDatabase.OutOfSyncFile;
import com.aerofs.daemon.core.status.db.SyncStatusRequests;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.SOID;

import org.slf4j.Logger;

import javax.inject.Inject;

import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is responsible for occasionally polling Polaris to verify that any
 * unsynced files really are out of sync. This results in eventual consistency
 * even if SSMP 'synced' notifications are missed for any reason.
 *
 * In order to reduce load on Polaris by allowing time for the normal sync
 * process to work, files recently marked out-of-sync are ignored.
 */
public class SyncStatusVerifier
        implements IShareListener, IDirectoryServiceListener, SyncStatusOnline.Listener
{
    private final static Logger l = Loggers.getLogger(SyncStatusVerifier.class);

    private final SyncStatusPropagator _syncStatusPropagator;
    private final SyncStatusOnline _syncStatusOnline;
    private final CoreScheduler _sched;
    private final TransManager _tm;
    private final DirectoryService _ds;
    private final OutOfSyncFilesDatabase _outOfSyncDatabase;
    private final CentralVersionDatabase _cvdb;
    private final SyncStatusRequests _syncStatusRequests;
    private final SyncStatusBatchStatusChecker _syncStatusBatchStatusChecker;
    private final SyncStatusUploadState _syncStatusUploadState;

    private volatile AtomicBoolean _eventScheduled;

    private final long SCHEDULE_DELAY = 30 * C.SEC;
    protected final long IGNORE_WINDOW = 2 * C.MIN;
    private final int MAX_BATCH_SIZE = 40;
    // page size is larger than batch size to reduce likelihood of unnecessary
    // db queries, as expelled OIDs are removed from the db rather than being
    // sent to polaris
    private final int PAGE_SIZE = MAX_BATCH_SIZE * 2;

    @Inject
    public SyncStatusVerifier(SyncStatusPropagator syncStatusPropagator,
            SyncStatusOnline syncStatusOnline, CoreScheduler coreScheduler, TransManager transManager,
            DirectoryService directoryService, OutOfSyncFilesDatabase outOfSyncDatabase,
            SyncStatusRequests syncStatusRequests, CentralVersionDatabase centralVersionDatabase,
            SyncStatusBatchStatusChecker syncStatusBatchStatusChecker,
            SyncStatusUploadState syncStatusUploadState) {
        _syncStatusPropagator = syncStatusPropagator;
        _syncStatusOnline = syncStatusOnline;
        _sched = coreScheduler;
        _tm = transManager;
        _ds = directoryService;
        _outOfSyncDatabase = outOfSyncDatabase;
        _syncStatusRequests = syncStatusRequests;
        _cvdb = centralVersionDatabase;
        _syncStatusBatchStatusChecker = syncStatusBatchStatusChecker;
        _syncStatusUploadState = syncStatusUploadState;

        _syncStatusOnline.addListener(this);
    }

    @Override
    public void onSyncStatusOnline_(boolean online) {
        l.trace("onSyncStatusOnline_: {}", online);
        if (online) {
            scheduleVerifyUnsyncedFilesImmediate(0L);
        } else {
            if (_eventScheduled != null) _eventScheduled.set(true);
        }
    }

    @Override
    public void onShare_(Trans t) {
        if (!_syncStatusOnline.get()) return;

        l.trace("onShare_");
        try {
            batchUpdateSyncStatus_(0L, 0L, t);
        } catch (SQLException e) {
            l.warn("error updating sync status after share", e);;
        }
    }

    @Override
    public void objectAdmitted_(SOID soid, Trans t) {
        if (!_syncStatusOnline.get()) return;

        try {
            batchUpdateSyncStatus_(0L, 0L, t);
        } catch (SQLException e) {
            l.warn("error updating sync status after admission", e);;
        }
    }

    protected void scheduleVerifyUnsyncedFilesImmediate(long nextPageStartingAfterIdx) {
        if (!_syncStatusOnline.get()) return;

        l.trace("scheduleVerifyUnsyncedFilesImmediate: {}", nextPageStartingAfterIdx);

        // cancel task if it's been scheduled in the future
        if (_eventScheduled != null) _eventScheduled.set(true);

        scheduleVerifyUnsyncedFiles(new PageableBatchUpdateEvent(nextPageStartingAfterIdx), 0);
    }

    protected void scheduleVerifyUnsyncedFilesDelay() {
        if (!_syncStatusOnline.get()) return;

        l.trace("scheduleVerifyUnsyncedFilesDelay");
        scheduleVerifyUnsyncedFiles(new PageableBatchUpdateEvent(0L), SCHEDULE_DELAY);
    }

    private void scheduleVerifyUnsyncedFiles(PageableBatchUpdateEvent event, long delay) {
        l.trace("scheduleVerifyUnsyncedFiles: {}", delay);
        if (_eventScheduled != null && !_eventScheduled.get()) return;

        _eventScheduled = _sched.scheduleCancellable(event, delay);
    }

    /*
     * Retrieves list of unsynced files from the OutOfSyncFilesDatabase, creates
     * a single request for the polaris LocationsBatch endpoint, and checks to
     * make sure they are really out of sync.
     */
    protected void batchUpdateSyncStatus_(long nextPageStartingAfterIdx, long ignoreWindow, Trans trans)
            throws SQLException {
        l.trace("enter batchUpdateSyncStatus_");
        if (!_syncStatusOnline.get()) return;

        Map<SOID, ResolvedPath> paths = new LinkedHashMap<>();
        Map<SOID, LocationStatusObject> operations = new LinkedHashMap<>();
        nextPageStartingAfterIdx = populateOutOfSyncsListAndReturnLastIdx_(paths, operations,
                nextPageStartingAfterIdx, ignoreWindow, trans);

        l.trace("operations.size: {}, nextPageStartingAfterIdx: {}", operations.size(),
                nextPageStartingAfterIdx);
        if (!operations.isEmpty()) {
            storePendingRequests_(trans, operations);
            boolean hasMore = nextPageStartingAfterIdx < Long.MAX_VALUE;
            _syncStatusBatchStatusChecker
                    .submitLocationStatusBatch(new LocationStatusBatch(operations.values()),
                            new PageableCallback(
                                    nextPageStartingAfterIdx),
                    batchResult -> scheduleUpdateSyncStatus_(operations, paths, batchResult, hasMore));
        } else if (nextPageStartingAfterIdx == Long.MAX_VALUE) {
            scheduleVerifyUnsyncedFilesDelay();
        } else {
            // try next page of results in another task to avoid monopolizing
            // the core lock
            scheduleVerifyUnsyncedFilesImmediate(nextPageStartingAfterIdx);
        }
        l.trace("exit batchUpdateSyncStatus_");
    }

    protected long populateOutOfSyncsListAndReturnLastIdx_(Map<SOID, ResolvedPath> paths,
            Map<SOID, LocationStatusObject> operations, long nextPageStartingAfterIdx,
            long ignoreWindow, Trans t) throws SQLException {
        l.trace("enter populateOutOfSyncsListAndReturnLastIdx_: {}, {}", nextPageStartingAfterIdx,
                ignoreWindow);
        do {
            Set<SOID> invalidSOIDs = new HashSet<>();
            try (IDBIterator<OutOfSyncFile> outOfSyncFiles = _outOfSyncDatabase
                    .selectPage_(nextPageStartingAfterIdx, PAGE_SIZE)) {
                int count = 0;
                while (outOfSyncFiles.next_() && operations.size() < MAX_BATCH_SIZE) {
                    count++;
                    OutOfSyncFile outOfSyncFile = outOfSyncFiles.get_();

                    // stop paging when results are within the IGNORE_WINDOW.
                    // safe because newest timestamps are guaranteed to be at
                    // the end of the resultSet.
                    if (outOfSyncFile.timestamp > System.currentTimeMillis() - ignoreWindow) break;

                    nextPageStartingAfterIdx = outOfSyncFile.idx;
                    SOID soid = new SOID(outOfSyncFile.sidx, outOfSyncFile.oid);

                    if (_syncStatusUploadState.contains(soid)) continue;

                    OA oa = _ds.getOANullable_(soid);
                    l.trace("oa: {}", oa);
                    boolean added = false;
                    if (oa != null && !oa.isExpelled()) {
                        ResolvedPath path = _ds.resolve_(oa);
                        Long version = _cvdb.getVersion_(soid.sidx(), soid.oid());
                        l.trace("path: {}, version: {}", path, version);
                        if (version != null) {
                            operations.put(soid, new LocationStatusObject(
                                    soid.oid().toStringFormal(), version));
                            paths.put(soid, path);
                            added = true;
                        }
                    }
                    if (!added) invalidSOIDs.add(soid);
                }
                if (operations.size() != MAX_BATCH_SIZE && count != PAGE_SIZE)
                    nextPageStartingAfterIdx = Long.MAX_VALUE;
            }
            for (SOID invalid : invalidSOIDs) {
                l.trace("removing {} from outOfSyncDatabase", invalid.toString());
                _outOfSyncDatabase.delete_(invalid.sidx(), invalid.oid(), t);
            }
        } while (operations.isEmpty() && nextPageStartingAfterIdx < Long.MAX_VALUE);

        l.trace("exit populateOutOfSyncsListAndReturnLastIdx_: {}", nextPageStartingAfterIdx);
        return nextPageStartingAfterIdx;
    }

    private void storePendingRequests_(Trans t, Map<SOID, LocationStatusObject> operations)
            throws SQLException {
        operations.entrySet()
                .forEach(op -> _syncStatusRequests.setSyncRequest(op.getKey(), op.getValue().version));
    }

    protected Boolean scheduleUpdateSyncStatus_(Map<SOID, LocationStatusObject> operations,
            Map<SOID, ResolvedPath> paths, LocationStatusBatchResult batchResult, boolean hasMore)
                    throws SQLException {
        l.trace("enter scheduleUpdateSyncStatus_");
        try (Trans t = _tm.begin_()) {
            updateSyncStatusBatch_(operations, paths, batchResult, t);
            t.commit_();
        }
        return hasMore;
    }

    protected void updateSyncStatusBatch_(Map<SOID, LocationStatusObject> operations,
            Map<SOID, ResolvedPath> paths, LocationStatusBatchResult batchResult, Trans t)
                    throws SQLException {
        l.trace("enter updateSyncStatusBatch_");
        Iterator<Entry<SOID, LocationStatusObject>> opsIterator = operations.entrySet()
                .iterator();
        for (boolean backedUp : batchResult.results) {
            Entry<SOID, LocationStatusObject> operation = opsIterator.next();
            SOID soid = operation.getKey();
            if (_syncStatusRequests.deleteSyncRequestIfVersionMatches(soid,
                    operation.getValue().version)) {
                l.trace("found matching request, updating sync status");
                OA oa = _ds.getOANullable_(soid);
                if (oa != null && !oa.isExpelled()) {
                    l.trace("updateSyncStatusBatch_ {}: {}", oa.soid(), backedUp);
                    _syncStatusPropagator.updateSyncStatus_(soid, paths.get(soid), backedUp, t);
                }
            } else {
                l.trace("did not find matching request, skipping sync status update");
            }
        }
        l.trace("leave updateSyncStatusBatch_");
    }

    private class PageableBatchUpdateEvent extends AbstractEBSelfHandling
    {
        private long nextPageStartingAfterIdx;

        public PageableBatchUpdateEvent(long nextPageStartingAfterIdx) {
            this.nextPageStartingAfterIdx = nextPageStartingAfterIdx;
        }

        @Override
        public void handle_() {
            if (!_syncStatusOnline.get()) return;
            l.trace("PageableBatchUpdateEvent.handle_: {}", nextPageStartingAfterIdx);

            try (Trans t = _tm.begin_()) {
                batchUpdateSyncStatus_(nextPageStartingAfterIdx, IGNORE_WINDOW, t);
                t.commit_();
            } catch (SQLException e) {
                l.warn("error batch updating sync status", e);
            }
        }
    }

    private class PageableCallback implements AsyncTaskCallback
    {
        private long nextPageStartingAfterIdx;

        public PageableCallback(long nextPageStartingAfterIdx) {
            this.nextPageStartingAfterIdx = nextPageStartingAfterIdx;
        }

        @Override
        public void onSuccess_(boolean hasMore) {
            l.trace("onSuccess_: {}", hasMore);
            if (hasMore && nextPageStartingAfterIdx < Long.MAX_VALUE) {
                scheduleVerifyUnsyncedFilesImmediate(nextPageStartingAfterIdx);
            } else {
                scheduleVerifyUnsyncedFilesDelay();
            }
        }

        @Override
        public void onFailure_(Throwable t) {
            l.error("error communicating with polaris, aborting", t);
            scheduleVerifyUnsyncedFilesDelay();
        }
    }
}
