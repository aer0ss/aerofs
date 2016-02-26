package com.aerofs.daemon.core.status;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.IDirectoryServiceListener;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.polaris.db.ContentFetchQueueDatabase;
import com.aerofs.daemon.core.polaris.db.ContentFetchQueueWrapper;
import com.aerofs.daemon.core.polaris.db.IContentFetchQueueListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.proto.PathStatus.PBPathStatus.Sync;

import org.slf4j.Logger;

import javax.inject.Inject;

import java.sql.SQLException;

import static com.aerofs.proto.PathStatus.PBPathStatus.Sync.IN_SYNC;

/**
 * Handles events that can change the sync status of an object and its parents.
 * Updates sync status to reflect the event with calls to
 * {@link SyncStatusPropagator}.
 */
public class SyncStatusChangeHandler implements IDirectoryServiceListener, IContentFetchQueueListener
{
    private static final Logger logger = Loggers.getLogger(SyncStatusChangeHandler.class);
    private final SyncStatusPropagator _syncStatusPropagator;
    private final DirectoryService _ds;
    private final ContentFetchQueueDatabase _cfqdb;

    @Inject
    public SyncStatusChangeHandler(SyncStatusPropagator syncStatusPropagator,
            DirectoryService directoryService, ContentFetchQueueDatabase cfqdb,
            ContentFetchQueueWrapper cfe) {
        this._syncStatusPropagator = syncStatusPropagator;
        this._ds = directoryService;
        this._cfqdb = cfqdb;
        this._ds.addListener_(this);
        cfe.addListener(this);
    }

    @Override
    public void objectCreated_(SOID soid, OID parent, Path pathTo, Trans t) throws SQLException {
        logger.trace("ENTER objectCreated_: {}, {}", soid, pathTo);
        if (this._cfqdb.exists_(soid.sidx(), soid.oid())) {
            if (pathTo instanceof ResolvedPath) {
                _syncStatusPropagator.updateSyncStatus_(soid, pathTo, false, t);
            } else {
                _syncStatusPropagator.updateSyncStatus_(soid, false, t);
            }
        } else if (_syncStatusPropagator.getSync_(soid, t) == IN_SYNC) {
            // notify shellext that this new file is synced in case shellext
            // requested its status before the OA was created
            _syncStatusPropagator.forceNotifyListeners(pathTo, IN_SYNC);
        }
        logger.trace("EXIT objectCreated_: {}, {}", soid, pathTo);
    }

    @Override
    public void objectDeleted_(SOID soid, OID parent, Path pathFrom, Trans t) throws SQLException {
        logger.trace("ENTER objectDeleted_: {}, {}", soid, pathFrom);
        OA oa = _ds.getOA_(soid);
        logger.trace("synced: {}, oosChildren: {}", oa.synced(), oa.oosChildren());
        if (oa.isExpelled() && !_syncStatusPropagator.getSync_(oa, t).equals(IN_SYNC)) {
            Path parentPath = pathFrom.removeLast();
            SOID parentSoid = _ds.resolveNullable_(parentPath);
            oa = _ds.getOA_(parentSoid);
            if (oa.isAnchor()) {
                parentSoid = _ds.followAnchorNullable_(oa);
            }
            _syncStatusPropagator.propagateSyncStatus_(parentSoid, parentPath, -1, t);
        }
        logger.trace("EXIT objectDeleted_: {}, {}", soid, pathFrom);
    }

    @Override
    public void objectMoved_(SOID soid, OID parentFrom, OID parentTo, Path pathFrom, Path pathTo,
            Trans t) throws SQLException {
        logger.trace("ENTER objectMoved_: {}, {} -> {}", soid, pathFrom, pathTo);
        if (_syncStatusPropagator.getSync_(soid, t) != IN_SYNC && !parentFrom.equals(parentTo)) {
            _syncStatusPropagator.propagateSyncStatus_(new SOID(soid.sidx(), parentFrom),
                    pathFrom.removeLast(), -1, t);
            _syncStatusPropagator.propagateSyncStatus_(new SOID(soid.sidx(), parentTo),
                    pathTo.removeLast(), 1, t);
        }
        logger.trace("EXIT objectMoved_: {}, {} -> {}", soid, pathFrom, pathTo);
    }

    @Override
    public void objectContentCreated_(SOKID sokid, Path path, Trans t) throws SQLException {
        logger.trace("ENTER objectContentCreated_: {}, {}", sokid.soid(), path);
        _syncStatusPropagator.updateSyncStatus_(sokid.soid(), path, false, t);
        logger.trace("EXIT objectContentCreated_: {}, {}", sokid.soid(), path);
    }

    @Override
    public void objectContentModified_(SOKID sokid, Path path, Trans t) throws SQLException {
        logger.trace("ENTER objectContentModified_: {}, {}", sokid.soid(), path);
        _syncStatusPropagator.updateSyncStatus_(sokid.soid(), path, false, t);
        logger.trace("EXIT objectContentModified_: {}, {}", sokid.soid(), path);
    }

    @Override
    public void objectContentDeleted_(SOKID sokid, Trans t) throws SQLException {
        if (logger.isTraceEnabled()) {
            logger.trace("ENTER objectContentDeleted_: {}, {}", sokid.soid(),
                    _ds.resolve_(sokid.soid()));
        }
        _syncStatusPropagator.updateSyncStatus_(sokid.soid(), false, t);
        logger.trace("EXIT objectContentDeleted_");
    }

    @Override
    public void objectObliterated_(OA oa, Trans t) throws SQLException {
        logger.trace("ENTER objectObliterated_: {}, {}", oa.soid(), oa);
        ResolvedPath path = _ds.resolve_(oa);
        SOID soid = _ds.resolveNullable_(path);
        logger.trace("objectObliterated_: path: {}, current soid: {}", path, soid);
        if (_syncStatusPropagator.getSync_(oa, t) != IN_SYNC || !oa.synced() && soid != null) {
            SOID parentSoid = new SOID(oa.soid().sidx(), oa.parent());
            Path parentPath = _ds.resolveNullable_(parentSoid);
            if (parentPath != null) {
                _syncStatusPropagator.propagateSyncStatus_(parentSoid, parentPath, -1, t);
            }
        }
        logger.trace("EXIT objectObliterated_: {}", oa.soid());
    }

    @Override
    public void objectExpelled_(SOID soid, Trans t) throws SQLException {
        logger.trace("ENTER objectExpelled: {}", soid);
        OA oa = _ds.getOANullable_(soid);

        if (oa == null) return;

        ResolvedPath expelled = _ds.resolveNullable_(soid);

        logger.trace("objectExpelled_ path: {}", expelled);

        Sync sync = _syncStatusPropagator.getSync_(oa, t);

        // no op if object is already expelled
        // N.B. this works because the oa cache entry is not invalidated until
        // after the listener is notified of the expulsion
        if (sync != IN_SYNC && !oa.isExpelled()) {
            logger.debug("expelled object is oos, propagating ignore");
            _syncStatusPropagator.propagateSyncStatus_(new SOID(soid.sidx(), oa.parent()),
                    expelled.parent(), -1, t);
        } else {
            logger.debug("objectExpelled_: NOP");
        }
        logger.trace("EXIT objectExpelled");
    }

    @Override
    public void objectAdmitted_(SOID soid, Trans t) throws SQLException {
        logger.trace("ENTER objectAdmitted_, {}", soid);
        OA oa = _ds.getOANullable_(soid);

        if (oa == null) return;

        logger.trace("objectAdmitted_: {}", oa);

        // no op if object isn't out of sync or wasn't expelled
        // N.B. this works because the oa cache entry is not invalidated until
        // after the listener is notified of the expulsion
        if (!oa.synced() && oa.isExpelled()) {
            logger.debug("propagating status for admitted soid");
            ResolvedPath path = _ds.resolve_(oa);
            _syncStatusPropagator.propagateSyncStatus_(new SOID(soid.sidx(), oa.parent()), path.parent(),
                    1, t);
        } else {
            logger.debug("objectAdmitted_: NOP");
        }
        logger.trace("EXIT objectAdmitted_, {}", soid);
    }

    @Override
    public void onInsert_(SIndex sidx, OID oid, Trans t) throws SQLException {
        logger.trace("ENTER onInsert_");
        _syncStatusPropagator.updateSyncStatus_(new SOID(sidx, oid), false, t);
        logger.trace("EXIT onInsert_");
    }
}
