package com.aerofs.daemon.core.syncstatus;

import java.sql.SQLException;
import java.util.Map;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.store.DeviceBitMap;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.lib.db.ISyncStatusDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Ritual.PBSyncStatus;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

/**
 * SyncStatus
 *
 * The low-level status information obtained from the central server is stored in
 * the local DB (see SyncStatusSynchronizer and SyncStatusNotificationSubscriber).
 * This class retrieves information from the DB and prepares it before it can be
 * exposed through the Ritual API.
 */
public class LocalSyncStatus
{
    private final DirectoryService _ds;
    private final ISyncStatusDatabase _ssdb;
    private final MapSIndex2DeviceBitMap _sidx2dbm;

    @Inject
    public LocalSyncStatus(DirectoryService ds, ISyncStatusDatabase ssdb,
            MapSIndex2DeviceBitMap sidx2dbm)
    {
        _ds = ds;
        _ssdb = ssdb;
        _sidx2dbm = sidx2dbm;
    }

    public Map<DID, PBSyncStatus.Status> getSyncStatusMap_(SOID soid) throws SQLException
    {
        DeviceBitMap dids = _sidx2dbm.getDeviceMapping_(soid.sidx());
        BitVector sync = _ds.getSyncStatus_(soid);
        Map<DID, PBSyncStatus.Status> m = Maps.newHashMap();
        for (int i = 0; i < dids.size(); ++i) {
            m.put(dids.get(i),
                  sync.test(i) ? PBSyncStatus.Status.IN_SYNC : PBSyncStatus.Status.IN_PROGRESS);
        }
        return m;
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
