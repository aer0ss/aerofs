package com.aerofs.daemon.core.phy;

import java.io.IOException;
import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;

import javax.annotation.Nullable;

/**
 * instances of this interface represent a combination of SOKIDs and file or directory paths.
 * They don't necessarily represent existing files/folders. This is very similar to how java.physical.File
 * represents abstract paths.
 */
public interface IPhysicalObject
{
    /**
     * @throws IOException if the physical object already exists or other I/O error occurs
     *
     * N.B. must roll back the operation if the transaction is aborted
     */
    void create_(PhysicalOp op, Trans t) throws IOException, SQLException;

    /**
     * @throws IOException if the object is a non-empty folder, or it doesn't exist, or other I/O
     * error occurs
     *
     * N.B. must roll back the operation if the transaction is aborted
     */
    @Nullable String delete_(PhysicalOp op, Trans t) throws IOException, SQLException;

    /**
     * If the physical storage uses OID in any way, update any and all information relative to
     * this physical object to account for the fact that the logical filesystem stopped using
     * the old OID in favor of a new one.
     *
     *
     * @param soid new OID used by the logical filesystem to refer to this physical object
     * @pre the object must have been created with the previously used OID
     */
    void updateSOID_(SOID soid, Trans t) throws IOException, SQLException;
}
