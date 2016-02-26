
package com.aerofs.daemon.core.status;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.status.db.OutOfSyncFilesDatabase;
import com.aerofs.daemon.core.status.db.SyncStatusRequests;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.ITransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.PathStatus.PBPathStatus.Sync;
import com.google.common.collect.ImmutableMap;

import org.slf4j.Logger;

import javax.inject.Inject;

import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import static com.aerofs.proto.PathStatus.PBPathStatus.Sync.IN_SYNC;
import static com.aerofs.proto.PathStatus.PBPathStatus.Sync.UNKNOWN;

/**
 * Responsible for keeping track of the sync status of each file using the sync
 * columns in the metaDatabase. Propagates any changes up to affected ancestors
 * as necessary.
 *
 * Sync status is tracked with two columns in the MetaDatabase, corresponding to
 * the synced() and oosChildren() methods in {@link OA}. synced() represents
 * whether or not the file/directory is currently synced, and oosChildren()
 * represents the number of out of sync children for a directory.
 *
 * The following is a description of the complexities of the primary methods.
 * "D" is used to represent the complexity of one database operation.
 *
 * getSync_(Path) requires converting the Path argument to a SOID, which results
 * in a worst-case complexity of O(log(N) + D). The path conversion is necessary
 * only because only the GUI has a need to retrieve the sync status, and it's
 * unaware of SOIDs. So, the propagator does the conversion from Path to SOID
 * for convenience. getSync_(Path, SOID) avoids this conversion, which results
 * in a worst-case complexity of O(D).
 *
 * updateSyncStatus_() requires traversing all the way up the file tree in the
 * worst case, it cannot achieve better than O(log(N)) complexity. Since it also
 * requires Paths in addition to SOIDs, it begins by getting a ResolvedPath
 * (O(log(N))), and makes calls to the constant-time ResolvedPath.parent()
 * method instead of repeatedly resolving the path while propagating. After
 * accounting for a constant amount of database operations per SOID, this
 * results in an O(log(N) * D) complexity overall.
 *
 * As an optimization to reduce load on the db during long transactions, the
 * number of database updates per folder during a single transaction is limited
 * to one. Since many files may be updated in a single transaction, there's no
 * reason to update a folder's out-of-sync children until its final
 * end-of-transaction number is known. In the meantime, the updated sync
 * information is kept in-memory in a TransLocal, and only saved to the db as
 * the transaction is committing {@link ITransListener#committing_(Trans)}.
 */
public class SyncStatusPropagator implements ISyncStatusPropagator
{
    private final static Logger l = Loggers.getLogger(SyncStatusPropagator.class);

    private final DirectoryService _ds;
    private final IMapSIndex2SID _sidx2sid;
    private final StoreHierarchy _stores;
    private final SyncStatusRequests _syncStatusRequests;
    private final OutOfSyncFilesDatabase _outOfSyncFilesDatabase;

    private final List<ISyncStatusListener> _listeners;
    private final TransLocal<Map<Path, Sync>> _transUpdatedFiles;
    private final TransLocal<Map<SOID, SyncStatus>> _transUpdatedFolders;

    @Inject
    public SyncStatusPropagator(DirectoryService directoryService, IMapSIndex2SID sidx2sid,
            StoreHierarchy stores, SyncStatusRequests syncStatusRequests,
            OutOfSyncFilesDatabase outOfSyncFilesDatabase) {
        _ds = directoryService;
        _sidx2sid = sidx2sid;
        _stores = stores;
        _syncStatusRequests = syncStatusRequests;
        _outOfSyncFilesDatabase = outOfSyncFilesDatabase;
        _listeners = new ArrayList<>();
        _transUpdatedFiles = new TransLocal<Map<Path, Sync>>() {
            @Override
            protected Map<Path, Sync> initialValue(Trans t) {
                Map<Path, Sync> syncNotifications = new ConcurrentHashMap<>();
                t.addListener_(new AbstractTransListener() {
                    @Override
                    public void committing_(Trans t) throws SQLException {
                        if (!syncNotifications.isEmpty() || !_transUpdatedFiles.get(t).isEmpty()) {
                            notifyRootSyncStatus_(t);
                        }
                    }

                    @Override
                    public void committed_() {
                        l.trace("updatedFiles.committed: {}", syncNotifications.size());
                        if (syncNotifications.isEmpty()) return;
                        notifyListeners(syncNotifications);
                    }
                });
                return syncNotifications;
            }
        };
        _transUpdatedFolders = new TransLocal<Map<SOID, SyncStatus>>() {
            @Override
            protected Map<SOID, SyncStatus> initialValue(Trans t) {
                Map<SOID, SyncStatus> updatedFolders = new ConcurrentHashMap<>();
                t.addListener_(new AbstractTransListener() {
                    @Override
                    public void committing_(Trans t) throws SQLException {
                        l.trace("updatedFolders.committing: {}", updatedFolders.size());
                        for (Entry<SOID, SyncStatus> statusChange : updatedFolders.entrySet()) {
                            _ds.setOASyncAttributes(statusChange.getKey(),
                                    statusChange.getValue().synced, statusChange.getValue().oosChildren,
                                    t);
                        }
                    }

                    @Override
                    public void committed_() {
                        l.trace("updatedFolders.committed: {}", updatedFolders.size());
                        if (updatedFolders.isEmpty()) return;

                        Map<Path, Sync> updates = new HashMap<>();
                        updatedFolders
                                .forEach((k, v) -> updates.put(v.path, v.synced ? IN_SYNC : UNKNOWN));
                        notifyListeners(updates);
                    }
                });
                return updatedFolders;
            }
        };
    }

    @Override
    public void addListener(ISyncStatusListener listener) {
        _listeners.add(listener);
    }

    private void notifyListeners(Map<Path, Sync> updates) {
        for (ISyncStatusListener listener : _listeners) {
            listener.onStatusChanged_(updates);
        }
    }

    @Override
    public void updateSyncStatus_(SOID soid, boolean synced, Trans t) throws SQLException {
        updateSyncStatus_(soid, null, synced, t);
    }

    public void updateSyncStatus_(SOID soid, Path path, boolean synced, Trans t) throws SQLException {
        _syncStatusRequests.deleteSyncRequest(soid);
        updateSyncStatus_(soid, path, synced ? IN_SYNC : UNKNOWN, t);
    }

    @Override
    public Sync getSync_(Path path) throws SQLException {
        l.trace("getSync path = {}", path);
        Sync status = getSync_(_ds.resolveNullable_(path));
        l.trace("getSync return {}", status);
        return status;
    }

    @Override
    public Sync getSync_(SOID soid) throws SQLException {
        l.trace("getSync soid = {}", soid);
        if (soid == null) return UNKNOWN;

        OA oa = _ds.getOANullable_(soid);
        return oa.isExpelled() ? UNKNOWN : getSync_(oa);
    }

    private Sync getSync_(OA oa) throws SQLException {
        l.trace("getSync {}", oa.soid());

        Sync sync = UNKNOWN;
        if (oa != null && oa.synced()) {
            sync = IN_SYNC;
        }
        l.trace("getSync {}: {}", oa.soid(), sync);
        return sync;
    }

    protected Sync getSync_(SOID soid, Trans t) throws SQLException {
        l.trace("getSync with trans, soid = {}", soid);
        if (soid == null) return UNKNOWN;

        OA oa = _ds.getOA_(soid);

        if (oa.isDirOrAnchor() && _transUpdatedFolders.get(t).containsKey(soid)) {
            SyncStatus syncStatus = _transUpdatedFolders.get(t).get(soid);
            l.trace("returning transLocal syncStatus.synced: {}", syncStatus.synced);
            return syncStatus.synced ? IN_SYNC : UNKNOWN;
        }

        return getSync_(oa);
    }

    protected Sync getSync_(OA oa, Trans t) throws SQLException {
        l.trace("getSync with trans, soid = {}", oa.soid());

        if (oa.isDirOrAnchor() && _transUpdatedFolders.get(t).containsKey(oa.soid())) {
            SyncStatus syncStatus = _transUpdatedFolders.get(t).get(oa.soid());
            l.trace("returning transLocal syncStatus.synced: {}", syncStatus.synced);
            return syncStatus.synced ? IN_SYNC : UNKNOWN;
        }

        return getSync_(oa);
    }

    private void updateSyncStatus_(SOID soid, Path path, Sync newStatus, Trans t) throws SQLException {
        l.trace("ENTER updateSyncStatus_, {}, {}: {}", soid, path, newStatus);

        OA oa = _ds.getOANullable_(soid);
        if (oa == null || oa.isExpelled() || oa.isDirOrAnchor()) {
            l.trace("Invalid oa, returning. soid: {}, null? {}, expelled? {}, dir? {}", soid, oa == null,
                    oa == null ? null : oa.isExpelled(), oa == null ? null : oa.isDirOrAnchor());
            return;
        }

        if (path == null) path = _ds.resolveNullable_(soid);

        Sync updatedStatus = _transUpdatedFiles.get(t).get(path);
        int oosChange = 0;
        boolean wasSynced = updatedStatus != null ? updatedStatus == IN_SYNC : oa.synced();
        boolean isSynced = newStatus == IN_SYNC;

        l.trace("{}, {} wasSynced: {}, isSynced: {}", soid, path, wasSynced, isSynced);

        updateOutOfSyncFilesDatabase(soid, t, isSynced);

        if (wasSynced && !isSynced) oosChange = 1;
        else if (!wasSynced && isSynced) oosChange = -1;

        if (oosChange != 0) {
            _ds.setOASyncAttributes(soid, isSynced, 0, t);
            _transUpdatedFiles.get(t).put(path, newStatus);
            propagateSyncStatus_(new SOID(soid.sidx(), oa.parent()), path.removeLast(), oosChange, t);
        } else if (isSynced) {
            // notify synced even if it's not changed to make sure that the gui
            // isn't out of sync
            _transUpdatedFiles.get(t).put(path, newStatus);
        }
        l.trace("EXIT updateSyncStatus_, {}, {}: {}", soid, path, newStatus);
    }

    private void updateOutOfSyncFilesDatabase(SOID soid, Trans t, boolean isSynced) throws SQLException {
        if (isSynced) {
            _outOfSyncFilesDatabase.delete_(soid.sidx(), soid.oid(), t);
        } else {
            _outOfSyncFilesDatabase.insert_(soid.sidx(), soid.oid(), t);
        }
    }

    /**
     * propagates sync status changes to a file up the tree, updating sync
     * status and the number of out-of-sync children as necessary. stops when
     * the folder's sync status is unaffected by the change to its number of
     * out-of-sync children
     *
     * @param oa
     */
    protected void propagateSyncStatus_(SOID soid, Path path, int oosChange, Trans t)
            throws SQLException {
        l.trace("ENTER: propagateSyncStatus_: {}, {}, {}", soid, path, oosChange);
        if (soid == null || path == null || oosChange == 0) return;

        while (oosChange != 0) {
            OA oa = _ds.getOANullable_(soid);
            if (oa == null || oa.isExpelled() || oa.isFile()) {
                l.trace("Invalid oa, returning. soid: {}, null? {}, expelled? {}, file? {}", soid,
                        oa == null, oa == null ? null : oa.isExpelled(),
                        oa == null ? null : oa.isFile());
                return;
            }

            l.trace("propagating {}, {}", path, soid);

            Map<SOID, SyncStatus> transUpdatedFolders = _transUpdatedFolders.get(t);
            long thisFileOOSChildren = transUpdatedFolders.containsKey(soid)
                    ? transUpdatedFolders.get(soid).oosChildren : oa.oosChildren();
            int parentOOSChildrenChange = calcParentOOSChildrenChange(oosChange, thisFileOOSChildren);

            if (thisFileOOSChildren + oosChange < 0) {
                l.warn("Error: setting to negative number.  Path: {}, oosChildren: {}", path,
                        thisFileOOSChildren);
                return;
            }

            thisFileOOSChildren += oosChange;

            SyncStatus status = new SyncStatus(path, thisFileOOSChildren == 0, thisFileOOSChildren);
            transUpdatedFolders.put(soid, status);

            l.trace("propagating {} - synced: {}, oosChildren: {}", path, status.synced,
                    status.oosChildren);

            oosChange = parentOOSChildrenChange;

            if (path.isEmpty()) break;

            SOID parentSoid = new SOID(oa.soid().sidx(), oa.parent());
            if (!soid.equals(parentSoid)) {
                soid = parentSoid;
                path = path.removeLast();
            } else {
                soid = getAnchor_(soid);
                if (soid == null) break;
            }
        }
        l.trace("EXIT: propagateSyncStatus_: {}", path);
    }

    private SOID getAnchor_(SOID soid) throws SQLException {
        SID sid = _sidx2sid.getLocalOrAbsent_(soid.sidx());

        if (sid == null) return null;

        Set<SIndex> parents = _stores.getParents_(soid.sidx());

        if (parents.size() != 1) return null;

        soid = new SOID(parents.iterator().next(), SID.storeSID2anchorOID(sid));
        return soid;
    }

    private int calcParentOOSChildrenChange(int oosChange, long thisFileOOSChildren) {
        int parentOOSChildrenChange = 0;
        if (thisFileOOSChildren == 0 && oosChange > 0) {
            parentOOSChildrenChange = 1;
        } else if (oosChange != 0 && thisFileOOSChildren + oosChange == 0) {
            parentOOSChildrenChange = -1;
        }
        return parentOOSChildrenChange;
    }

    protected void notifyRootSyncStatus_() throws SQLException {
        l.trace("enter notifyRootSyncStatus_");
        SOID rootSoid = new SOID(new SIndex(1), OID.ROOT);
        notifyListeners(ImmutableMap.of(_ds.resolve_(rootSoid),
                _ds.getOA_(rootSoid).synced() ? IN_SYNC : UNKNOWN));
        l.trace("leave notifyRootSyncStatus_");
    }

    private void notifyRootSyncStatus_(Trans t) throws SQLException {
        l.trace("enter notifyRootSyncStatus_");
        SOID rootSoid = new SOID(new SIndex(1), OID.ROOT);

        // add to updatedFiles transLocal to force notification
        Map<Path, Sync> transLocalFolders = _transUpdatedFiles.get(t);
        ResolvedPath rootPath = _ds.resolve_(rootSoid);
        if (transLocalFolders.containsKey(rootPath)) {
            transLocalFolders.put(rootPath, getSync_(rootSoid, t));
        }
        l.trace("leave notifyRootSyncStatus_");
    }

    public void forceNotifyListeners(Path path, Sync syncStatus) {
        notifyListeners(ImmutableMap.of(path, syncStatus));
    }

    private static final class SyncStatus
    {
        public final Path path;
        public final boolean synced;
        public final long oosChildren;

        public SyncStatus(Path path, boolean synced, long oosChildren) {
            this.path = path;
            this.synced = synced;
            this.oosChildren = oosChildren;
        }
    }
}
