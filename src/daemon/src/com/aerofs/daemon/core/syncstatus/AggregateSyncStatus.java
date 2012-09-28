/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.syncstatus;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IDirectoryServiceListener;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.CounterVector;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.sql.SQLException;

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

    @Inject
    public AggregateSyncStatus(DirectoryService ds, MapSIndex2DeviceBitMap sidx2dbm)
    {
        _ds = ds;
        _sidx2dbm = sidx2dbm;

        ds.addListener_(this);
    }

    /**
     * Recursively update aggregate sync status of parent
     * @param parent directory whose aggregate sync status is to be updated
     * @param diffStatus sync status change of children (1 : status changed, 0: status unchanged)
     * @param newStatus new sync status of children
     * Note: if the changed child is a file the status vectors passed to this function are regular
     * sync status vectors, if the child is a folder, aggregate sync status vectors are passed
     * instead.
     */
    private void updateRecursively_(SOID parent, BitVector diffStatus, BitVector newStatus,
            int parentSyncableChildCountDiff, Trans t)
            throws SQLException
    {
        OA oaParent = _ds.getOA_(parent);
        // invariant : anchors don't get aggregate sync status
        assert !oaParent.isAnchor();

        int first = diffStatus.findFirstSetBit();
        if (parentSyncableChildCountDiff == 0 && first == -1) {
            if (l.isInfoEnabled()) l.info("no change " + parent);
            return;
        }

        // compute parent diff
        BitVector parentStatus = _ds.getSyncStatus_(parent);
        int total = getSyncableChildCount_(parent);
        CounterVector parentAggregate = _ds.getAggregateSyncStatus_(parent);

        int deviceCount = _sidx2dbm.getDeviceMapping_(parent.sidx()).size();
        BitVector parentDiffStatus = parentAggregate.elementsEqual(
                    total - parentSyncableChildCountDiff, deviceCount);
        parentDiffStatus.andInPlace(parentStatus);

        if (l.isInfoEnabled()) {
            l.info("update " + parent + " " + diffStatus + " " + newStatus + " " + parentAggregate);
            l.info("\t" + parentSyncableChildCountDiff + " " + parentStatus + " " + parentDiffStatus);
        }

        for (int i = first; i != -1; i = diffStatus.findNextSetBit(i + 1)) {
            int prev = parentAggregate.get(i);
            if (newStatus.test(i)) {
                // out_sync -> in_sync
                assert prev < total;
                parentAggregate.inc(i);
            } else {
                // in_sync -> out_sync
                assert prev > 0;
                parentAggregate.dec(i);
            }
        }

        if (l.isInfoEnabled())
            l.info(" -> " + parentAggregate);
        _ds.setAggregateSyncStatus_(parent, parentAggregate, t);

        // terminate recursive update at store root or if change stops cascading
        if (parent.oid().isRoot()) return;

        // derive new aggregte sync status vector for parent
        BitVector parentNewStatus = parentAggregate.elementsEqual(total, deviceCount);
        parentNewStatus.andInPlace(parentStatus);

        // compute the actual diff
        parentDiffStatus.xorInPlace(parentNewStatus);

        // recursively update
        SOID grandparent = new SOID(parent.sidx(), oaParent.parent());
        if (l.isInfoEnabled())
            l.info("pupdate " + grandparent + " " + parentDiffStatus +  " " + parentNewStatus);

        updateRecursively_(grandparent, parentDiffStatus, parentNewStatus, 0, t);
    }

    /**
     * Called from DirectoryService when a new object is created (or moved out of trash)
     */
    @Override
    public void objectCreated_(SOID soid, OID parent, Path pathTo, Trans t) throws SQLException
    {
        if (l.isInfoEnabled())
            l.info("created " + soid + " " + parent);
        updateParentAggregateOnCreation_(soid, new SOID(soid.sidx(), parent), t);
    }


    /**
     * Called from DirectoryService when an existing object is deleted (i.e. moved to trash)
     */
    @Override
    public void objectDeleted_(SOID soid, OID parent, Path pathFrom, Trans t) throws SQLException
    {
        if (l.isInfoEnabled())
            l.info("deleted " + soid + " " + parent);
        updateParentAggregateOnDeletion_(soid, new SOID(soid.sidx(), parent), t);
    }

    /**
     * Called from DirectoryService when an existing object is moved
     */
    @Override
    public void objectMoved_(SOID soid, OID parentFrom, OID parentTo, Path pathFrom,
            Path pathTo, Trans t) throws SQLException
    {
        // ignore renames, we'll be notified in objectSyncStatusChanged_()
        if (parentFrom.equals(parentTo)) return;
        if (l.isInfoEnabled())
            l.info("moved " + soid + " " + parentFrom + " " + parentTo);
        updateParentAggregateOnDeletion_(soid, new SOID(soid.sidx(), parentFrom), t);
        updateParentAggregateOnCreation_(soid, new SOID(soid.sidx(), parentTo), t);
    }

    /**
     * Called from DirectoryService when an object is modified (change in the CA table)
     */
    @Override
    public void objectContentModified_(SOID soid, Path path, boolean firstBranchCreated, Trans t)
            throws SQLException
    {
        if (!firstBranchCreated) return;

        BitVector status = _ds.getSyncStatus_(soid);
        if (status.isEmpty()) return;

        // first CA creation for an object with non-empty sync status: successful download of
        // recently readmitted object -> need to update parent aggregate
        OA oa = _ds.getOA_(soid);
        assert oa.isFile() : soid;

        if (l.isInfoEnabled())
            l.info("synced readmitted file " + soid + " " + status);

        updateRecursively_(new SOID(soid.sidx(), oa.parent()), status, status, 0, t);
    }

    /**
     * Called from DirectoryService when an object is expelled
     */
    @Override
    public void objectExpelled_(SOID soid, Trans t) throws SQLException
    {
        OA oa = _ds.getOA_(soid);
        SOID parent = new SOID(soid.sidx(), oa.parent());

        // ignore expulsion resulting from object deletion (moving to trash)
        if (DirectoryService.isDeleted(_ds, oa)) return;

        if (l.isInfoEnabled())
            l.info("expelled " + soid);
        updateParentAggregateOnDeletion_(soid, parent, t);
    }

    /**
     * Called from DirectoryService when an expelled object is readmitted
     */
    @Override
    public void objectAdmitted_(SOID soid, Trans t) throws SQLException
    {
        OA oa = _ds.getOA_(soid);
        SOID parent = new SOID(soid.sidx(), oa.parent());

        if (l.isInfoEnabled())
            l.info("admitted " + soid + " " + _ds.getSyncStatus_(soid));
        updateParentAggregateOnCreation_(soid, parent, t);
    }

    /**
     * Called from DirectoryService when the sync status of an object changes
     */
    @Override
    public void objectSyncStatusChanged_(SOID soid, BitVector oldStatus, BitVector newStatus,
            Trans t) throws SQLException
    {
        if (l.isInfoEnabled())
            l.info("sschanged " + soid + " " + oldStatus + " " + newStatus);

        OA oa = _ds.getOA_(soid);
        // expelled objects are not taken into account by aggregate sync status
        if (oa.isExpelled()) return;

        SOID parent = new SOID(soid.sidx(), oa.parent());

        // for directories, take aggregate sync status into account
        if (oa.isDir()) {
            BitVector aggregateStatus = getAggregateSyncStatusVector_(soid);
            oldStatus.andInPlace(aggregateStatus);
            newStatus.andInPlace(aggregateStatus);
        }

        updateRecursively_(parent, oldStatus.xor(newStatus), newStatus, 0, t);
    }

    /**
     * Helper: calls {@link #updateRecursively_} with the appropriate arguments to indicate an
     * object deletion
     */
    private void updateParentAggregateOnDeletion_(SOID soid, SOID parent, Trans t)
            throws SQLException
    {
        // get old sync status, this will be the diff
        BitVector oldStatus = _ds.getSyncStatus_(soid);

        // for directories, take aggregate sync status into account
        // anchors are treated as regular files as their children are aggregated on lookup
        OA oa = _ds.getOA_(soid);
        if (oa.isDir()) {
            oldStatus.andInPlace(getAggregateSyncStatusVector_(soid));
        }

        updateRecursively_(parent, oldStatus, new BitVector(), -1, t);
    }

    /**
     * Helper: calls {@link #updateRecursively_} with the appropriate arguments to indicate an
     * object creation
     */
    private void updateParentAggregateOnCreation_(SOID soid, SOID parent, Trans t)
            throws SQLException
    {
        // aggregation stops at store boundary
        assert !soid.oid().isRoot() : soid + " " + parent;

        OA oa = _ds.getOA_(soid);
        // expelled objects are only created when new META is received for an object whose parent
        // is known locally but expelled. No actual CONTENT is being created so no file/folder
        // is created either and aggregate sync status can therefore safely ignore these events
        if (oa.isExpelled()) return;

        BitVector status = _ds.getSyncStatus_(soid);
        if (oa.isDir()) {
            status.andInPlace(getAggregateSyncStatusVector_(soid));
        }

        if (!status.isEmpty() && !(oa.isFile() && oa.caMasterNullable() == null)) {
            // the object being added has some sync status, full update required unless it is a file
            // without any content (i.e. recently readmitted), in which case part of the update is
            // delayed until some version of the content is resynced
            if (l.isInfoEnabled())
                l.info("update parent on creation " + parent);

            updateRecursively_(parent, status, status, 1, t);
        } else if (!parent.oid().isRoot()) {
            // if the parent was in sync, it will automatically become out of sync due to the way
            // the final sync status is derived from the aggregate. However the aggregate for the
            // grandparent (and its parents) may need to be updated explicitly.
            SOID grandparent = new SOID(parent.sidx(), _ds.getOA_(parent).parent());

            if (l.isInfoEnabled())
                l.info("update grandparent on creation " + grandparent);

            CounterVector parentAggregate = _ds.getAggregateSyncStatus_(parent);
            int deviceCount = _sidx2dbm.getDeviceMapping_(parent.sidx()).size();

            BitVector parentStatus = parentAggregate.elementsEqual(
                    getSyncableChildCount_(parent) - 1, deviceCount);
            parentStatus.andInPlace(_ds.getSyncStatus_(parent));

            updateRecursively_(grandparent, parentStatus, new BitVector(), 0, t);
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
