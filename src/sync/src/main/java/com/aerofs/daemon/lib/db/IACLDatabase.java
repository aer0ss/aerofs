package com.aerofs.daemon.lib.db;

import com.aerofs.base.acl.Permissions;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.ids.UserID;

import java.sql.SQLException;
import java.util.Map;

/**
 * To be implemented by any class that performs CRUD operations on the ACL persistent store
 */
public interface IACLDatabase
{
    /**
     * Retrieve the list of subjects and roles for the given store
     * @param sidx {@code SIndex} for the store for which the ACL should be retrieved
     * @return a {@link IDBIterator} over the acl for the specified store which returns a pair from
     * subject=>role on each {@link com.aerofs.lib.db.IDBIterator#get_()} call
     */
    IDBIterator<Map.Entry<UserID, Permissions>> get_(SIndex sidx) throws SQLException;

    /**
     * @return local acl epoch for the user
     * @throws SQLException if there are database errors on acl lookup
     */
    long getEpoch_() throws SQLException;

    /**
     * Adds entries to the acl for the specified {@code sid}
     *
     * @param sidx {@code SIndex} for the store for which the acl should be set
     * @param subject2role a map of subject=>role they have for this store
     * @param t transaction (this method can only be called as part of a transaction)
     * @throws SQLException if there are db errors during the update
     */
    void set_(SIndex sidx, Map<UserID, Permissions> subject2role, Trans t) throws SQLException;

    /**
     * Delete entries from the acl for the specified {@code sid}
     *
     * @param sidx {@code SIndex} for the store from which these roles should be deleted
     * @param subject the user who should be removed from this store's acl
     * @param t transaction (this method can only be called as part of a transaction)
     * @throws SQLException if there are db errors during the update
     */
    void delete_(SIndex sidx, UserID subject, Trans t) throws SQLException;

    /**
     * Clear all the entries for the given store
     * @param t transaction (this method can only be called as part of a transaction)
     * @throws SQLException if there are db errors during the update
     */
    void clear_(SIndex sidx, Trans t) throws SQLException;

    /**
     * Store the acl ecpoch for this user
     * <br/>
     * <br/>
     * <strong>IMPORTANT:</strong> this method should not be called directly! The only place from
     * which this method should be used is {@link com.aerofs.daemon.core.acl.ACLSynchronizer}.
     * Use the methods defined there; they will update the epoch number correctly
     *
     * @param newEpoch new epoch to store in the database
     * @param t transaction (this method can only be called as part of a transaction)
     * @throws SQLException if there are db errors during the update
     */
    void setEpoch_(long newEpoch, Trans t) throws SQLException;

    /**
     *
     */
    IDBIterator<SIndex> getAccessibleStores_(UserID subject) throws SQLException;
}
