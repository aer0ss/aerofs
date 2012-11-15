/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.syncstatus;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IDirectoryServiceListener;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.CounterVector;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Sync status is known on a per-object granularity. However knowing that the name of a folder is
 * in or out of sync is of little interest compared to knowing that its contents (i.e children) are
 * in sync, hence the decision to recursively aggregate sync status.
 *
 * To mitigate the huge cost of recursive aggregation, some caching must be done. To avoid a
 * significant startup cost, that cache needs to be persistent. To avoid costly recomputation, this
 * cache is update-based rather than invalidation-based and stores more internal state than what
 * it returns in queries to allow fast incremental updates.
 *
 * Similarly to the regular sync status, the aggregate sync status is intrinsically linked to an
 * object so it makes sense to store it in an extra column of the object attribute table
 * {@link com.aerofs.daemon.lib.db.IMetaDatabase}. Contrary to regular sync status, this column is
 * only used for directories.
 *
 * Although the aggregate sync status is technically a bitvector, we actually store a vector of
 * integers in the DB (see {@link CounterVector}). A number of IN_SYNC children is associated to
 * each DID (mapping done through {@link com.aerofs.daemon.core.store.DeviceBitMap}) from which the
 * aggregate sync status can be derived by a comparison to the total number of syncable (ie. not
 * expelled, not anchor) children.
 *
 * The aggregated values stored in the DB only convey the sync status *within* the store which is
 * then aggregated, at lookup time, with the aggregated sync status of the root directories of all
 * the stores residing under the directory being looked up. This approach slightly increases lookup
 * complexity but vastly decreases update complexity and is key to keeping the storage schema simple
 *
 * This representation has a non-negligible space overhead (32 bits per DID instead of 1) but
 * allows O(d) updates instead of O(d*c) (where d is the max depth of the directory tree and c the
 * max number of children per directory)
 *
 * The aggregate sync status is cleared upon expulsion but sync status is kept up-to-date for
 * expelled object. When a file is readmitted, it is considered out of sync until it is first
 * synced. This behavior is prefered over simply clearing the sync status upon expulsion because
 * the sync status server will not send notifications when it receives an unchanged version hash
 * which would leave unchanged readmitted files without sync status until the next change.
 */
public class AggregateSyncStatus implements IDirectoryServiceListener
{
    private static final Logger l = Util.l(AggregateSyncStatus.class);

    private final DirectoryService _ds;
    private final MapSIndex2DeviceBitMap _sidx2dbm;

    public static interface IListener
    {
        void syncStatusChanged_(Set<Path> changes);
    }

    private final List<IListener> _listeners;

    @Inject
    public AggregateSyncStatus(DirectoryService ds, MapSIndex2DeviceBitMap sidx2dbm)
    {
        _ds = ds;
        _sidx2dbm = sidx2dbm;
        _listeners = Lists.newArrayList();

        ds.addListener_(this);
    }

    public void addListener_(IListener listener)
    {
        _listeners.add(listener);
    }

    /**
     * Track set of SOIDs whose status is affected by a transaction to be able to send relevant
     * ritual notifications when the transaction is commited.
     */
    private final TransLocal<Set<Path>> _tlStatusModified
            = new TransLocal<Set<Path>>() {
        @Override
        protected Set<Path> initialValue(Trans t)
        {
            final Set<Path> set = Sets.newHashSet();
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committed_() {
                    l.warn("Path list updated by transaction:");
                    for (Path p : set) {
                        l.warn("  " + p);

                        // aggressive consistency checking
                        // need to be done at the end of the transaction to avoid false positives
                        // due to two step updates (e.g move is delete+create)
                        // TODO: disable once we're confident all bugs have been squashed
                        // (this completely destroys the performance benefits of maintanining
                        // aggregated status as it essentially recomputes it...)
                        try {
                            SOID soid = _ds.resolveNullable_(p);
                            if (soid == null) continue;
                            OA oa = _ds.getOA_(soid);
                            if (!oa.isDir()) return;
                            checkAggregateConsistency(soid, _ds.getAggregateSyncStatus_(soid));
                        } catch (SQLException e) {
                            // bury exception
                        }
                    }
                    for (IListener listener : _listeners) {
                        listener.syncStatusChanged_(set);
                    }
                }
            });
            return set;
        }
    };

    /**
     * Local consistency-check of aggregate sync status
     *
     * Compares the result of upward incremental update to downward aggregation.
     */
    private void checkAggregateConsistency(SOID soid, CounterVector expected) throws SQLException
    {
        SIndex sidx = soid.sidx();
        CounterVector cv = new CounterVector();
        try {
            for (OID coid : _ds.getChildren_(soid)) {
                OA coa = _ds.getOA_(new SOID(soid.sidx(), coid));
                SOID csoid = new SOID(sidx, coid);
                if (!coa.isExpelled()) {
                    BitVector cbv = _ds.getSyncStatus_(csoid);
                    if (coa.isDir())
                        cbv.andInPlace(getAggregateSyncStatusVector_(csoid));
                    for (int i = cbv.findFirstSetBit(); i != -1; i = cbv.findNextSetBit(i + 1)) {
                        cv.inc(i);
                    }
                }
            }
        } catch (ExNotDir e) {
            assert false : soid;
        } catch (ExNotFound e) {
            assert false : soid;
        }
        assert cv.equals(expected) : soid + " " + cv + " != " + expected;
    }

    /**
     * Recursively update aggregate sync status of parent
     * @param parent directory whose aggregate sync status is to be updated
     * @param diffStatus sync status change of children (1 : status changed, 0: status unchanged)
     * @param newStatus new sync status of children
     * @param parentSyncableChildCountDiff number of syncable children created by the transaction
     * @param path path of {@code parent}
     * (or deleted, if negative)
     * Note: if the changed child is a file the status vectors passed to this function are regular
     * sync status vectors, if the child is a folder, aggregate sync status vectors are passed
     * instead.
     */
    private void updateRecursively_(SOID parent, BitVector diffStatus, BitVector newStatus,
            int parentSyncableChildCountDiff, Path path, Trans t)
            throws SQLException
    {
        OA oaParent = _ds.getOA_(parent);
        // invariant : aggregation stop at store root, anchors are treated as regular file within
        // the parent store
        assert !oaParent.isAnchor();

        int first = diffStatus.findFirstSetBit();
        if (parentSyncableChildCountDiff == 0 && first == -1) {
            if (l.isInfoEnabled()) l.info("no change " + parent);
            return;
        }

        // compute parent diff
        int total = getSyncableChildCount_(parent);
        CounterVector parentAggregate = _ds.getAggregateSyncStatus_(parent);
        int deviceCount = _sidx2dbm.getDeviceMapping_(parent.sidx()).size();

        BitVector parentStatus;
        if (parent.oid().isRoot()) {
            // store roots don't have sync status
            parentStatus = new BitVector(deviceCount, true);
        } else {
            parentStatus = _ds.getSyncStatus_(parent);
        }
        BitVector parentDiffStatus = parentAggregate.elementsEqual(
                    total - parentSyncableChildCountDiff, deviceCount);
        parentDiffStatus.andInPlace(parentStatus);

        if (l.isInfoEnabled()) {
            l.info("update " + parent + " " + diffStatus + " " + newStatus);
            l.info("\t" + parentAggregate + " " + parentStatus + " " + parentDiffStatus);
            l.info("\t" + parentSyncableChildCountDiff + " " + total + " " + deviceCount);
        }

        for (int i = first; i != -1; i = diffStatus.findNextSetBit(i + 1)) {
            int prev = parentAggregate.get(i);
            if (newStatus.test(i)) {
                // out_sync -> in_sync
                assert prev < total : parent + " " + path + " prev: " + prev + " total: " + total;
                parentAggregate.inc(i);
            } else {
                // in_sync -> out_sync
                assert prev > 0 : parent + " " + path + " prev: " + prev;
                parentAggregate.dec(i);
            }
        }

        if (l.isInfoEnabled()) {
            l.info(" -> " + parentAggregate);
        }

        // when tracking elusive bugs, you may uncomment the line below to enforce aggressive
        // consistency-checking
        //checkAggregateConsistency(parent, parentAggregate);

        _ds.setAggregateSyncStatus_(parent, parentAggregate, t);

        // derive new aggregte sync status vector for parent
        BitVector parentNewStatus = parentAggregate.elementsEqual(total, deviceCount);
        parentNewStatus.andInPlace(parentStatus);

        // compute the actual diff
        parentDiffStatus.xorInPlace(parentNewStatus);

        if (parentDiffStatus.isEmpty()) {
            if (l.isInfoEnabled()) l.info("cascading stopped");
            return;
        }

        // keep track of new status for Ritual notifications
        _tlStatusModified.get(t).add(path);

        // terminate recursive update at store root or if change stops cascading
        if (parent.oid().isRoot()) {
            // notify beyond store root (if it's not root store)...
            while (!path.isEmpty()) {
                path = path.removeLast();
                _tlStatusModified.get(t).add(path);
            }
            return;
        }

        // recursively update
        SOID grandparent = new SOID(parent.sidx(), oaParent.parent());
        if (l.isInfoEnabled())
            l.info("pupdate " + grandparent + " " + parentDiffStatus +  " " + parentNewStatus);

        updateRecursively_(grandparent, parentDiffStatus, parentNewStatus, 0, path.removeLast(), t);
    }

    /**
     * Called from DirectoryService when a new object is created (or moved out of trash)
     */
    @Override
    public void objectCreated_(SOID soid, OID parent, Path pathTo, Trans t) throws SQLException
    {
        if (l.isInfoEnabled()) l.info("created " + soid + " " + parent);

        updateParentAggregateOnCreation_(soid, new SOID(soid.sidx(), parent), pathTo, t);
    }

    /**
     * Called from DirectoryService when an existing object is deleted (i.e. moved to trash)
     */
    @Override
    public void objectDeleted_(SOID soid, OID parent, Path pathFrom, Trans t) throws SQLException
    {
        if (l.isInfoEnabled()) l.info("deleted " + soid + " " + parent);

        updateParentAggregateOnDeletion_(soid, new SOID(soid.sidx(), parent), pathFrom, t);
    }

    /**
     * Called from DirectoryService when an existing object is *removed from the DB*
     *
     * NOTE: This callback is guaranteed to only be called for non-expelled objects
     */
    @Override
    public void objectObliterated_(OA oa, BitVector bv, Path pathFrom, Trans t)
            throws SQLException
    {
        if (l.isInfoEnabled()) l.info("obliterated " + oa.soid() + " " + oa.parent());

        updateParentAggregateOnDeletion_(new SOID(oa.soid().sidx(), oa.parent()), bv, pathFrom, t);
    }

    /**
     * Called from DirectoryService when an existing object is moved
     */
    @Override
    public void objectMoved_(SOID soid, OID parentFrom, OID parentTo, Path pathFrom,
            Path pathTo, Trans t) throws SQLException
    {
        // ignore renames, we'll be notified in objectSyncStatusChanged_()
        if (parentFrom.equals(parentTo)) {
            BitVector status = _ds.getSyncStatus_(soid);
            if (!status.isEmpty()) {
                // keep track of new status for Ritual notifications
                _tlStatusModified.get(t).add(pathFrom);
                _tlStatusModified.get(t).add(pathTo);
            }
            return;
        }

        if (l.isInfoEnabled()) l.info("moved " + soid + " " + parentFrom + " " + parentTo);

        updateParentAggregateOnDeletion_(soid, new SOID(soid.sidx(), parentFrom), pathFrom, t);
        updateParentAggregateOnCreation_(soid, new SOID(soid.sidx(), parentTo), pathTo, t);
    }

    /**
     * Called from DirectoryService when an object is modified (change in the CA table)
     */
    @Override
    public void objectContentModified_(SOID soid, Path path, boolean firstBranchCreated, Trans t)
            throws SQLException
    {
        if (!firstBranchCreated) return;

        BitVector status = _ds.getRawSyncStatus_(soid);
        if (status.isEmpty()) return;

        // first CA creation for an object with non-empty sync status: successful download of
        // recently readmitted object -> need to update parent aggregate
        OA oa = _ds.getOA_(soid);
        assert oa.isFile() : soid;

        if (l.isInfoEnabled()) l.info("synced readmitted file " + soid + " " + status);

        // keep track of new status for Ritual notifications
        _tlStatusModified.get(t).add(path);

        SOID parent = new SOID(soid.sidx(), oa.parent());
        updateRecursively_(parent, status, status, 0, path.removeLast(), t);
    }

    /**
     * Called from DirectoryService when an object is expelled
     */
    @Override
    public void objectExpelled_(SOID soid, Trans t) throws SQLException
    {
        OA oa = _ds.getOA_(soid);
        assert oa.isExpelled() : soid;
        Path path = _ds.resolve_(soid);
        SOID parent = new SOID(soid.sidx(), oa.parent());

        // NOTE: it would theoretically be possible to handle expulsion resulting from deletion
        // and selective sync the same way which one might think would be cleaner but it would
        // involve limiting the upward propagation at the deletion boundary which would actually
        // end up being quite cumbersome. Therefore we simply reset aggregate sync status upon
        // expulsion resulting from deletion
        if (_ds.isDeleted_(oa)) {
            if (oa.isDir()) _ds.setAggregateSyncStatus_(soid, new CounterVector(), t);
            return;
        }

        if (l.isInfoEnabled()) l.info("expelled " + soid);
        updateParentAggregateOnDeletion_(soid, parent, path, t);
    }

    /**
     * Called from DirectoryService when an expelled object is readmitted
     */
    @Override
    public void objectAdmitted_(SOID soid, Trans t) throws SQLException
    {
        OA oa = _ds.getOA_(soid);
        assert !oa.isExpelled() : soid;
        Path path = _ds.resolve_(soid);
        SOID parent = new SOID(soid.sidx(), oa.parent());

        if (l.isInfoEnabled()) l.info("admitted " + soid + " " + _ds.getSyncStatus_(soid));
        updateParentAggregateOnCreation_(soid, parent, path, t);
    }

    /**
     * Called from DirectoryService when the sync status of an object changes
     */
    @Override
    public void objectSyncStatusChanged_(SOID soid, BitVector oldStatus, BitVector newStatus,
            Trans t) throws SQLException
    {
        if (l.isInfoEnabled()) l.info("sschanged " + soid + " " + oldStatus + " " + newStatus);

        OA oa = _ds.getOA_(soid);
        // expelled objects are not taken into account by aggregate sync status
        if (oa.isExpelled()) return;

        Path path = _ds.resolve_(soid);
        SOID parent = new SOID(soid.sidx(), oa.parent());

        // for directories, take aggregate sync status into account
        if (oa.isDir()) {
            BitVector aggregateStatus = getAggregateSyncStatusVector_(soid);
            oldStatus.andInPlace(aggregateStatus);
            newStatus.andInPlace(aggregateStatus);
        }

        // compute diff between old and new status
        oldStatus.xorInPlace(newStatus);

        if (oldStatus.isEmpty()) return;

        // keep track of new status for Ritual notifications
        _tlStatusModified.get(t).add(path);

        updateRecursively_(parent, oldStatus, newStatus, 0, path.removeLast(), t);
    }

    /**
     * Helper: calls {@link #updateRecursively_} with the appropriate arguments to indicate an
     * object deletion
     */
    private void updateParentAggregateOnDeletion_(SOID soid, SOID parent, Path path, Trans t)
            throws SQLException
    {
        // get old sync status, this will be the diff
        BitVector oldStatus = _ds.getSyncStatus_(soid);

        OA oa = _ds.getOA_(soid);
        // expelled objects are not take into account in the aggregate sync status of their parent
        // so we don't have anything to update in this case
        if (oa.isExpelled()) {
            if (l.isInfoEnabled()) l.info("ignore delete expelled " + soid);
            return;
        }

        // for directories, take aggregate sync status into account
        // anchors are treated as regular files as their children are aggregated on lookup
        if (oa.isDir()) {
            oldStatus.andInPlace(getAggregateSyncStatusVector_(soid));
        }
        updateParentAggregateOnDeletion_(parent, oldStatus, path, t);
    }

    private void updateParentAggregateOnDeletion_(SOID parent, BitVector oldStatus, Path path,
            Trans t) throws SQLException {
        /*
         * Do not send notifications for deleted path
         *  - the physical object is deleted so the notification is pointless
         *  - deletion/expulsion group all affected object in a single transaction which could
         *  make the TransLocal map huge and lead to an OOM...
         *
         * Because expulsion/deletion notifications are received from a postfix walk (leaves first
         * and moving upwards) we are certain that removal at this point overwrites all previous
         * additions caused by upward updates on children deletion. Similarly we do not prevent
         * the upward update from adding to the TransLocal map because we don't know at this point
         * from how far up the deletion is coming and we still want to send updates for non-deleted
         * objects whose aggregate status is affected by the deletion.
         */
        _tlStatusModified.get(t).remove(path);

        updateRecursively_(parent, oldStatus, new BitVector(), -1, path.removeLast(), t);
    }

    /**
     * Helper: calls {@link #updateRecursively_} with the appropriate arguments to indicate an
     * object creation
     */
    private void updateParentAggregateOnCreation_(SOID soid, SOID parent, Path path, Trans t)
            throws SQLException
    {
        // aggregation stops at store boundary
        assert !soid.oid().isRoot() : soid + " " + parent;

        OA oa = _ds.getOA_(soid);
        // expelled objects are only created when new META is received for an object whose parent
        // is known locally but expelled. No actual CONTENT is being created so no file/folder
        // is created either and aggregate sync status can therefore safely ignore these events
        if (oa.isExpelled()) {
            if (l.isInfoEnabled()) l.info("ignore create expelled " + soid);
            return;
        }

        // files without any content (i.e. recently readmitted), may have old sync status in the DB
        // but DirectoryService automatically filters it out until the file is re-synced.
        BitVector status = _ds.getSyncStatus_(soid);
        if (oa.isDir()) {
            status.andInPlace(getAggregateSyncStatusVector_(soid));
        }

        Path ppath = path.removeLast();

        // always send notification for creation to avoid caching problems
        // i.e if a file with sync status is deleted and a new file is created with the same name,
        // Ritual clients (shellext and co) may still show the sync status of the deleted file until
        // the status of the new file changes for the first time (which may take a long time if no
        // peers are online).
        _tlStatusModified.get(t).add(path);

        if (!status.isEmpty()) {
            // the object being added has some sync status, full update required
            // this happens when objects are moved from one parent to another

            if (l.isInfoEnabled()) l.info("update parent on creation " + parent);

            updateRecursively_(parent, status, status, 1, ppath, t);
        } else if (!parent.oid().isRoot()) {
            // if the parent was in sync, it will automatically become out of sync due to the way
            // the final sync status is derived from the aggregate. However the aggregate for the
            // grandparent (and its parents) may need to be updated explicitly.
            SOID grandparent = new SOID(parent.sidx(), _ds.getOA_(parent).parent());

            CounterVector parentAggregate = _ds.getAggregateSyncStatus_(parent);
            int deviceCount = _sidx2dbm.getDeviceMapping_(parent.sidx()).size();

            if (l.isInfoEnabled())
                l.info("update grandparent on creation " + grandparent + " " + parentAggregate);

            BitVector parentStatus = parentAggregate.elementsEqual(
                    getSyncableChildCount_(parent) - 1, deviceCount);
            parentStatus.andInPlace(_ds.getSyncStatus_(parent));

            if (parentStatus.isEmpty()) {
                if (l.isInfoEnabled()) l.info("cascading stopped at grandparent " + grandparent);
                return;
            }

            // keep track of new status for Ritual notifications
            _tlStatusModified.get(t).add(ppath);

            updateRecursively_(grandparent, parentStatus, new BitVector(), 0, ppath.removeLast(), t);
        }
    }

    /**
     * @pre {@code soid} exists and is a directory
     * @return the number of children relevant for aggregate sync status (non-expelled children)
     *
     * NOTE: Keep consistent with {@link com.aerofs.daemon.core.update.DPUTAddAggregateSyncColumn}
     */
    private int getSyncableChildCount_(SOID soid) throws SQLException
    {
        // TODO(huguesb): cache that number?
        int total = 0;
        try {
            for (OID oid : _ds.getChildren_(soid)) {
                OA coa = _ds.getOA_(new SOID(soid.sidx(), oid));
                if (!coa.isExpelled()) {
                    ++total;
                }
            }
        } catch (ExNotDir e) {
            assert false : soid;
        } catch (ExNotFound e) {
            assert false : soid;
        }
        return total;
    }

    /**
     * @pre {@code soid} is a directory
     * @return a bitvector representation of the aggregate sync status
     *
     * The bitvector is derived from the internal counter vector representation and appropriately
     * sized to match the number of devices known to share the store.
     */
    BitVector getAggregateSyncStatusVector_(SOID soid) throws SQLException
    {
        CounterVector cv = _ds.getAggregateSyncStatus_(soid);
        // make sure the resulting bitvector has the right size, this is especially important when
        // the number of syncable children is 0 to avoid inconsistent results.
        int deviceCount = _sidx2dbm.getDeviceMapping_(soid.sidx()).size();
        return cv.elementsEqual(getSyncableChildCount_(soid), deviceCount);
    }
}
