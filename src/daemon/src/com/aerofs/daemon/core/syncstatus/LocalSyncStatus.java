package com.aerofs.daemon.core.syncstatus;

import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.DeviceBitMap;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.lib.db.ISyncStatusDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.ex.ExExpelled;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;

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
 * Further processing for human-friendliness (using user and device names instead of IDs and taking
 * network status into account) is done in {@link HdGetSyncStatus}.
 */
public class LocalSyncStatus
{
    private final static Logger l = Util.l(LocalSyncStatus.class);

    private final IStores _stores;
    private final IMapSIndex2SID _sidx2sid;
    private final DirectoryService _ds;
    private final ISyncStatusDatabase _ssdb;
    private final MapSIndex2DeviceBitMap _sidx2dbm;
    private final AggregateSyncStatus _assc;

    @Inject
    public LocalSyncStatus(DirectoryService ds, IStores stores, ISyncStatusDatabase ssdb,
            IMapSIndex2SID sidx2sid, MapSIndex2DeviceBitMap sidx2dbm, AggregateSyncStatus assc)
    {
        _ds = ds;
        _stores = stores;
        _ssdb = ssdb;
        _sidx2sid = sidx2sid;
        _sidx2dbm = sidx2dbm;
        _assc = assc;
    }

    /**
     * Aggregate sync status recursively across store boundaries
     */
    public Map<DID, Boolean> getFullyAggregatedSyncStatusMap_(SOID soid)
            throws SQLException, ExExpelled
    {
        OA oa = _ds.getOA_(soid);
        l.info("aggregate " + soid);
        Map<DID, Boolean> aggregated = getStoreAggregatedSyncStatusMap_(soid, oa.isDir());
        if (oa.isAnchor()) {
            SOID root = _ds.followAnchorThrows_(oa);
            aggregateStoresUnder_(root, aggregated);
        } else if (oa.isDir()) {
            aggregateStoresUnder_(soid, aggregated);
        }
        return aggregated;
    }

    /**
     * Aggregate sync status of all store roots located under a given SOID
     */
    private void aggregateStoresUnder_(SOID soid, Map<DID, Boolean> aggregated) throws SQLException
    {
        for (SIndex sidx : _stores.getDescendants_(soid)) {
            // child SID, OID and root
            SID csid = _sidx2sid.get_(sidx);
            SOID csoid = new SOID(_stores.getParent_(sidx), SID.storeSID2anchorOID(csid));
            SOID croot = _ds.followAnchorNullable_(_ds.getOA_(csoid));
            // the anchor will be null for expelled stores
            if (croot != null) {
                l.info("aggregate store " + croot);
                aggregateStatusAcrossStores(aggregated,
                        getStoreAggregatedSyncStatusMap_(croot, true));
            }
        }
    }

    /**
     * Compute the final sync status (regular + in-store aggregate) and convert it into a
     * map representation suitable for aggregation across stores.
     *
     * @pre {@code soid} must not be expelled
     * @param hasAggregate whether the object is expected to have aggregate sync status
     */
    public Map<DID, Boolean> getStoreAggregatedSyncStatusMap_(SOID soid, boolean hasAggregate)
            throws SQLException
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

        l.info("-> " + status);

        DeviceBitMap dbm = _sidx2dbm.getDeviceMapping_(soid.sidx());
        Map<DID, Boolean> m = Maps.newHashMap();
        for (int i = 0; i < dbm.size(); ++i) {
            m.put(dbm.get(i), status.test(i));
        }
        return m;
    }

    /**
     * Aggregate two maps using AND
     */
    private static void aggregateStatusAcrossStores(@Nonnull Map<DID, Boolean> aggregated,
                                                    @Nonnull Map<DID, Boolean> child)
    {
        for (Entry<DID, Boolean> e : child.entrySet()) {
            Boolean a = aggregated.get(e.getKey());
            boolean b = e.getValue();
            aggregated.put(e.getKey(), a == null ? b : a && b);
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
     * see {@link ISyncStatusDatabase#setPullEpoch_}
     */
    public void setPullEpoch_(long newEpoch, Trans t) throws SQLException
    {
        _ssdb.setPullEpoch_(newEpoch, t);
    }

    /**
     * see {@link ISyncStatusDatabase#getPushEpoch_}
     */
    public long getPushEpoch_() throws SQLException
    {
        return _ssdb.getPushEpoch_();
    }

    /**
     * see {@link ISyncStatusDatabase#setPushEpoch_}
     */
    public void setPushEpoch_(long newIndex, Trans t) throws SQLException
    {
        _ssdb.setPushEpoch_(newIndex, t);
    }

    /**
     * see {@link ISyncStatusDatabase#getBootstrapSOIDs_}
     */
    public IDBIterator<SOID> getBootstrapSOIDs_() throws SQLException
    {
        return _ssdb.getBootstrapSOIDs_();
    }

    /**
     * see {@link ISyncStatusDatabase#removeBootstrapSOID_}
     */
    public void removeBootsrapSOID_(SOID soid, Trans t) throws SQLException
    {
        _ssdb.removeBootstrapSOID_(soid, t);
    }
}
