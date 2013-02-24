/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.ds;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;

import java.sql.SQLException;

/**
 * Interface for DirectoryService listeners
 *
 * All the methods are called during a transaction
 */
public interface IDirectoryServiceListener
{
    void objectCreated_(SOID obj, OID parent, Path pathTo, Trans t) throws SQLException;
    void objectDeleted_(SOID obj, OID parent, Path pathFrom, Trans t) throws SQLException;
    void objectMoved_(SOID obj, OID parentFrom, OID parentTo,
            Path pathFrom, Path pathTo, Trans t) throws SQLException;

    void objectContentCreated_(SOKID obj, Path path, Trans t) throws SQLException;
    void objectContentDeleted_(SOKID obj, Path path, Trans t) throws SQLException;
    void objectContentModified_(SOKID obj, Path path, Trans t) throws SQLException;

    void objectExpelled_(SOID obj, Trans t) throws SQLException;
    void objectAdmitted_(SOID obj, Trans t) throws SQLException;

    void objectSyncStatusChanged_(SOID obj, BitVector oldStatus, BitVector newStatus, Trans t)
            throws SQLException;

    /**
     * Called from deleteOA_ *after* the object is removed from the DB
     * This is necessary to properly cleanup temporary objects created by Aliasing
     *
     * IMPORTANT: hold on to the given OA as long as needed, the OID is *gone* from the DB
     */
    void objectObliterated_(OA oa, BitVector bv, Path pathFrom, Trans t) throws SQLException;
}