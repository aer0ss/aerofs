package com.aerofs.daemon.lib.db;

import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;

/**
 * Instances of this class store the SOID of all the objects with OA.FLAG_EXPELLED_ORG set. See
 * Expulsion.java for detail.
 */
public interface IExpulsionDatabase
{
    /**
     * Add the specified object id into the database. The id must not exist in the database.
     */
    void insertExpelledObject_(SOID soid, Trans t) throws SQLException;

    /**
     * Remove the specified object id from the database. The id must exist in the database.
     */
    void deleteExpelledObject_(SOID soid, Trans t) throws SQLException;

    /**
     * Delete all the expelled objects associated with {@code sidx}.
     */
    void deleteStore_(SIndex sidx, Trans t) throws SQLException;

    IDBIterator<SOID> getExpelledObjects_() throws SQLException;
}
