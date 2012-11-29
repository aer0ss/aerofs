package com.aerofs.daemon.core.syncstatus;

import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.DescendantStores;
import com.aerofs.daemon.core.store.DeviceBitMap;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.core.store.IStoreDeletionOperator;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.db.ISyncStatusDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExExpelled;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

/**
 * SyncStatus
 *
 * The low-level status information obtained from the central server is stored in the local DB
 * (see SyncStatusSynchronizer and SyncStatusNotificationSubscriber for details).
 *
 * This class retrieves information from the DB and prepares it before it can be exposed through the
 * Ritual API. First it "decompresses" the low overhead internal representation into a higher level
 * representation. Then it recursively aggregates child sync status to compute a more intuitive
 * sync status for directories.
 *
 * The aggregation is done through a generic algorithm and can be customized by reimplementing
 * {@link IAggregatedStatus}. The two most useful forms of aggregation are provided by default:
 *  - sync status map:DID->Boolean gives a detailed view of the relative status of each remote
 *  device sharing the object of interest
 *  - sync status summary gives a condensed summary of the sync status of the file for all devices
 *  through two booleans (allInSync and isPartiallySynced) (See {@link SyncStatusSummary})
 *
 * Further processing for human-friendliness (using user and device names instead of IDs and taking
 * network status into account) is done in {@link HdGetSyncStatus}.
 */
public class LocalSyncStatus implements IStoreDeletionOperator
{
    private final static Logger l = Util.l(LocalSyncStatus.class);

    private final DirectoryService _ds;
    private final ISyncStatusDatabase _ssdb;
    private final MapSIndex2DeviceBitMap _sidx2dbm;
    private final AggregateSyncStatus _assc;
    private final DescendantStores _dss;

    @Inject
    public LocalSyncStatus(DirectoryService ds, ISyncStatusDatabase ssdb,
            MapSIndex2DeviceBitMap sidx2dbm, AggregateSyncStatus assc,
            StoreDeletionOperators storeDeletionOperators, DescendantStores dss)
    {
        _ds = ds;
        _ssdb = ssdb;
        _sidx2dbm = sidx2dbm;
        _assc = assc;
        _dss = dss;
        storeDeletionOperators.add_(this);
    }

    /**
     * Aggregate sync status recursively across store boundaries
     */
    public Map<DID, Boolean> getFullyAggregatedSyncStatusMap_(SOID soid)
            throws SQLException, ExExpelled
    {
        SyncStatusMap m = new SyncStatusMap();
        aggregateAcrossStores_(soid, m);
        return m.d;
    }

    /**
     * Interface used for sync status aggregation of a single object
     *
     * Aggregation is done in two steps. First an IAggregatedStatus instance is created for each
     * store and merging is done for all devices within the store, then all these instances are
     * merged into the instance corresponding to the top level store (in no particular order).
     */
    public static interface IAggregatedStatus
    {
        /**
         * @return A clean aggregated status for a new store
         */
        public IAggregatedStatus create();

        /**
         * Aggregate status within a store
         * NOTE: called at most once per object
         */
        public void mergeDevices_(DeviceBitMap dbm, BitVector status);

        /**
         * Aggregate status of child store
         */
        public void mergeStore_(IAggregatedStatus aggregatedStatus);
    }

    /**
     * IAggregatedStatus implementation for detailed aggregation (used by GetSyncStatus)
     */
    public static class SyncStatusMap implements IAggregatedStatus
    {
        public Map<DID, Boolean> d = Maps.newHashMap();

        @Override
        public IAggregatedStatus create()
        {
            return new SyncStatusMap();
        }

        @Override
        public void mergeDevices_(DeviceBitMap dbm, BitVector status)
        {
            for (int i = 0; i < dbm.size(); ++i) {
                d.put(dbm.get(i), status.test(i));
            }
        }

        @Override
        public void mergeStore_(IAggregatedStatus aggregated)
        {
            SyncStatusMap m = (SyncStatusMap)aggregated;
            for (Entry<DID, Boolean> e : m.d.entrySet()) {
                Boolean a = d.get(e.getKey());
                Boolean b = e.getValue();
                d.put(e.getKey(), a == null ? b : a && b);
            }
        }
    }


    /**
     * Generic sync status aggregation across store boundaries
     * @pre {@code soid} must not be expelled
     */
    public void aggregateAcrossStores_(Path path, IAggregatedStatus aggregated)
            throws SQLException, ExExpelled, ExNotFound
    {
        aggregateAcrossStores_(_ds.resolveThrows_(path), aggregated);
    }

    /**
     * Generic sync status aggregation across store boundaries
     * @pre {@code soid} must not be expelled
     */
    public void aggregateAcrossStores_(SOID soid, IAggregatedStatus aggregated)
            throws SQLException, ExExpelled
    {
        OA oa = _ds.getOA_(soid);
        l.debug("aggregate across stores " + soid);
        aggregateWithinStore_(soid, oa.isDir(), aggregated);
        if (oa.isDir()) {
            // aggregate stores strictly under this directory
            aggregateDescendants_(soid, aggregated);
        } else if (oa.isAnchor()) {
            SOID root = _ds.followAnchorThrows_(oa);

            // aggregate root of this store
            IAggregatedStatus saggregate = aggregated.create();
            aggregateWithinStore_(root, true, saggregate);
            aggregated.mergeStore_(saggregate);

            // aggregate child stores strictly under the root of this store
            aggregateDescendants_(root, aggregated);
        }
    }

    /**
     * Generic sync status aggregation within store boundary
     * @param hasAggregate whether the object is expected to have aggregate sync status
     */
    private void aggregateWithinStore_(SOID soid, boolean hasAggregate,
            IAggregatedStatus aggregated) throws SQLException
    {
        BitVector status;
        if (soid.oid().isRoot()) {
            assert hasAggregate : soid;
            status = _assc.getAggregateSyncStatusVector_(soid);
        } else {
            status = _ds.getSyncStatus_(soid);
            if (hasAggregate) {
                status.andInPlace(_assc.getAggregateSyncStatusVector_(soid));
            }
        }

        l.debug("-> " + status);

        DeviceBitMap dbm = _sidx2dbm.getDeviceMapping_(soid.sidx());
        aggregated.mergeDevices_(dbm, status);
    }

    /**
     * Generic sync status aggregation of all store roots located under a given SOID
     */
    private void aggregateDescendants_(SOID soid, IAggregatedStatus aggregated) throws SQLException
    {
        for (SIndex sidx : _dss.getDescendantStores_(soid)) {
            SOID root = new SOID(sidx, OID.ROOT);
            // all descendant stores must be admitted
            assert _ds.hasOA_(root) : root + " " + soid;
            l.debug("aggregate descendants " + root);
            IAggregatedStatus saggregate = aggregated.create();
            aggregateWithinStore_(root, true, saggregate);
            aggregated.mergeStore_(saggregate);
        }
    }

    /**
     * see {@link ISyncStatusDatabase#getPullEpoch_}
     */
    public long getPullEpoch_() throws SQLException
    {
        return _ssdb.getPullEpoch_();
    }

    /**
     * see {@link ISyncStatusDatabase#getPushEpoch_}
     */
    public long getPushEpoch_() throws SQLException
    {
        return _ssdb.getPushEpoch_();
    }

    @Override
    public void deleteStore_(SIndex sidx, Trans t)  throws SQLException
    {
        _ssdb.deleteModifiedObjectsForStore_(sidx, t);
    }
}
