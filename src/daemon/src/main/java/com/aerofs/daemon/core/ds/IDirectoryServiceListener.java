/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.ds;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
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
    default void objectCreated_(SOID obj, OID parent, Path pathTo, Trans t) throws SQLException {}
    default void objectDeleted_(SOID obj, OID parent, Path pathFrom, Trans t) throws SQLException {}
    default void objectMoved_(SOID obj, OID parentFrom, OID parentTo,
            Path pathFrom, Path pathTo, Trans t) throws SQLException {}

    default void objectContentCreated_(SOKID obj, Path path, Trans t) throws SQLException {}
    default void objectContentModified_(SOKID obj, Path path, Trans t) throws SQLException {}
    default void objectContentDeleted_(SOKID obj, Trans t) throws SQLException {}

    /**
     * Called from deleteOA_ *after* the object is removed from the DB
     * This is necessary to properly cleanup temporary objects created by Aliasing
     *
     * IMPORTANT: hold on to the given OA as long as needed, the OID is *gone* from the DB
     */
    default void objectObliterated_(OA oa, Trans t) throws SQLException {}

    // NB: only called for explicitly expelled object
    // NOT for every implicitly expelled children
    default void objectExpelled_(SOID soid, Trans t) throws SQLException {}

    // NB: only called for explicitly expelled object
    // NOT for every implicitly expelled children
    default void objectAdmitted_(SOID soid, Trans t) throws SQLException {}
}
