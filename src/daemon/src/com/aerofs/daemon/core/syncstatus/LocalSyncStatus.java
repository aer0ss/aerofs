package com.aerofs.daemon.core.syncstatus;

import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.store.Stores;
import com.aerofs.daemon.core.store.Stores.IDIDBiMap;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.ISyncStatusDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Ritual.PBSyncStatus;
import com.google.inject.Inject;

/**
 * SyncStatus
 *
 * The low-level status information obtained from the central server is stored in
 * the local DB (see SyncStatusSynchronizer and SyncStatusNotificationSubscriber).
 * This class retrieves information from the DB and prepares it before it can be
 * exposed through the Ritual API.
 */
public class LocalSyncStatus {
    private final DevicePresence _dp;
    private final IMetaDatabase _mdb;
    private final ISyncStatusDatabase _ssdb;
    private final Stores _stores;

    @Inject
    public LocalSyncStatus(DevicePresence dp, IMetaDatabase mdb,
            ISyncStatusDatabase ssdb, Stores stores)
    {
        _dp = dp;
        _mdb = mdb;
        _ssdb = ssdb;
        _stores = stores;
    }

    private boolean isDeviceReachable(DID did) {
        return _dp.getOPMDevice_(did) != null;
    }

    public Map<DID, PBSyncStatus.Status> getSyncStatusMap_(SOID soid)
            throws SQLException {
        IDIDBiMap dids = _stores.getDeviceMapping_(soid.sidx());
        BitVector sync = _mdb.getSyncStatus_(soid);
        Map<DID, PBSyncStatus.Status> m = new TreeMap<DID, PBSyncStatus.Status>();
        for (int i = 0; i < dids.size(); ++i) {
            DID did = dids.get(i);
            PBSyncStatus.Status status = PBSyncStatus.Status.OFFLINE;
            if (sync.test(i)) {
                status = PBSyncStatus.Status.IN_SYNC;
            } else if (isDeviceReachable(did)) {
                status = PBSyncStatus.Status.IN_PROGRESS;
            }
            m.put(did, status);
        }
        return m;
    }

    /**
     * see {@link ISyncStatusDatabase.getPullEpoch_}
     */
    public long getPullEpoch_() throws SQLException {
        return _ssdb.getPullEpoch_();
    }

    /**
     * see {@link ISyncStatusDatabase.setPullEpoch_}
     */
    public void setPullEpoch_(long newEpoch, Trans t) throws SQLException {
        _ssdb.setPullEpoch_(newEpoch, t);
    }

    /**
     * see {@link ISyncStatusDatabase.getPushEpoch_}
     */
    public long getPushEpoch_() throws SQLException {
        return _ssdb.getPushEpoch_();
    }

    /**
     * see {@link ISyncStatusDatabase.setPushEpoch_}
     */
    public void setPushEpoch_(long newIndex, Trans t) throws SQLException {
        _ssdb.setPushEpoch_(newIndex, t);
    }

    /**
     * see {@link ISyncStatusDatabase.getBottstrapSOIDs_}
     */
    public IDBIterator<SOID> getBootstrapSOIDs_() throws SQLException {
        return _ssdb.getBootstrapSOIDs_();
    }

    /**
     * see {@link ISyncStatusDatabase.removeBootstrapSOID_}
     */
    public void removeBootsrapSOID_(SOID soid, Trans t) throws SQLException {
        _ssdb.removeBootsrapSOID_(soid, t);
    }

    /**
     * see {@link IMetaDatabase.getSyncStatus_}
     */
    public BitVector getSyncStatus_(SOID soid) throws SQLException {
        return _mdb.getSyncStatus_(soid);
    }

    /**
     * see {@link IMetaDatabase.setSyncStatus_}
     */
    public void setSyncStatus_(SOID soid, BitVector status, Trans t) throws SQLException {
        _mdb.setSyncStatus_(soid, status, t);
    }

    /**
     * see {@link IMetaDatabase.clearSyncStatus_}
     */
    public void clearSyncStatus_(SOID soid, Trans t) throws SQLException {
        _mdb.clearSyncStatus_(soid, t);
    }

    /**
     * see {@link IStoreDatabase.getDeviceMapping_}
     */
    public IDIDBiMap getDeviceMapping_(SIndex sidx) throws SQLException {
        return _stores.getDeviceMapping_(sidx);
    }

    /**
     * see {@link IStoreDatabase.addDevice_}
     */
    public int addDevice_(SIndex sidx, DID did, Trans t) throws SQLException {
        return _stores.addDevice_(sidx, did, t);
    }
}
