package com.aerofs.daemon.lib.db;

import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SOID;

/**
 * SyncStatus : each SOID as an associated bitvector representing its sync status
 * Each bit in said vector corresponds to a device sharing the SOID (which may or
 * may not belong to the same user) and indicates whether the device in question
 * has the same version of the file as the local device.
 *
 * The pull epoch is used to control the sync status data being pulled from the
 * central server. It is issued by the server as part of a GetSyncStatusReply.
 *
 * The push epoch is used to control the version hashes being sent to the server.
 * It corresponds to an index in the activity log table.
 *
 * Each store as an associated device list.
 */
public interface ISyncStatusDatabase {

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
    void removeBootsrapSOID_(SOID soid, Trans t) throws SQLException;
}
