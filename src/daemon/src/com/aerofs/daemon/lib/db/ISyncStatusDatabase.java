package com.aerofs.daemon.lib.db;

import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;

/**
 * The sync status database is actually split in multiple tables. The actual sync status data is
 * stored in the object attribute table and can be accessed (at the lowest level) through
 * {@link IMetaDatabase}. The per-store device mapping required to make sense of this data is itself
 * stored (unsurprisignly) in {@link IStoreDatabase}.
 *
 * Finally, this class controls access to push and pull epochs used by
 * {@link com.aerofs.daemon.core.syncstatus.SyncStatusSynchronizer} as well as the bootstrap table
 * which is only used on the first launch after the first update to a syncstatus-enabled client.
 *
 * NOTE: Whenever possible, use {@link com.aerofs.daemon.core.syncstatus.LocalSyncStatus} instead of
 * this class.
 */
public interface ISyncStatusDatabase
{

    /**
     * The local sync status pull epoch is the epoch of the last update received from
     * the sync status server. Epoch are increasing with each update but the rate of
     * increase is unspecified.
     * @return local sync status epoch for the device
     * @throws SQLException if there are database errors on lookup
     */
    long getPullEpoch_() throws SQLException;

    /**
     * Store the sync status pull epoch for this device
     * <br/>
     * <br/>
     * <strong>IMPORTANT:</strong> this method should not be called directly! The only place from
     * which this method should be used is {@link com.aerofs.daemon.core.syncstatus.SyncStatusSynchronizer}.
     * Use the methods defined there; they will update the epoch number correctly
     *
     * @param newEpoch new epoch to store in the database
     * @param t transaction (this method can only be called as part of a transaction)
     * @throws SQLException if there are db errors during the update
     */
    void setPullEpoch_(long newEpoch, Trans t) throws SQLException;

    /**
     * @return index of the last activity log item that caused a successful push to the sync
     * status server
     * @throws SQLException
     */
    long getPushEpoch_() throws SQLException;

    /**
     * Store the index of the last activity log item successfully pushed to the sync status
     * server.
     * <br/>
     * <br/>
     * <strong>IMPORTANT:</strong> this method should not be called directly! The only place from
     * which this method should be used is {@link com.aerofs.daemon.core.syncstatus.SyncStatusSynchronizer}.
     * Use the methods defined there; they will update the epoch number correctly
     *
     * @param newIndex new index to store in the database
     * @throws SQLException
     */
    void setPushEpoch_(long newIndex, Trans t) throws SQLException;

    /**
     * Mark all non-expelled objects as modified (to force re-sending vh)
     */
    void bootstrap_(Trans t) throws SQLException;

    /**
     * Iterate over push queue, in order of insertion
     *
     * Do not use outside of SyncStatusSynchronizer
     */
    public static class ModifiedObject {
        // the index of the row in the database. used for paging
        public final long _idx;

        // the identifier of the subject of the activity
        public final SOID _soid;

        public ModifiedObject(long idx, SOID soid) {
            _idx = idx;
            _soid = soid;
        }
    }
    /**
     * Return all object IDs with activities more recent than {@code from}.
     * @param from set to Long.MAX_VALUE to return all the activities
     */
    IDBIterator<ModifiedObject> getModifiedObjects_(long from) throws SQLException;

    /**
     * Add an SOID to the push queue (i.e. schedule sending a version hash)
     *
     * Do not use outside of SyncStatusSynchronizer
     */
    void addToModifiedObjects_(SOID soid, Trans t) throws SQLException;

    /**
     * Remove all push queue entries up to given index (including)
     *
     * Do not use outside of SyncStatusSynchronizer
     */
    void removeModifiedObjects_(long idx, Trans t) throws SQLException;

    void deleteModifiedObjectsForStore_(SIndex sidx, Trans t) throws SQLException;
}
