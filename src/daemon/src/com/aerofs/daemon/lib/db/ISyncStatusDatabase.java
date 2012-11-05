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
     * Bootstrap SOIDs are SOIDs that existed before sync status was implemented
     * @return a bootstrap SOID, null if bootstrap table is empty
     */
    IDBIterator<SOID> getBootstrapSOIDs_() throws SQLException;

    /**
     * Remove an OID from the bootstrap table
     * <br/>
     * <br/>
     * <strong>IMPORTANT:</strong> this method should not be called directly! The only place from
     * which this method should be used is {@link com.aerofs.daemon.core.syncstatus.SyncStatusSynchronizer}.
     * Use the methods defined there; they will update the epoch number correctly
     * @throws SQLException
     */
    void removeBootstrapSOID_(SOID soid, Trans t) throws SQLException;

    void deleteBootstrapSOIDsForStore_(SIndex sidx, Trans t) throws SQLException;
}
