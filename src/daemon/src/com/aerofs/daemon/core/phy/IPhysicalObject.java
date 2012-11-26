package com.aerofs.daemon.core.phy;

import java.io.IOException;
import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;

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
    void delete_(PhysicalOp op, Trans t) throws IOException, SQLException;

    /**
     * @throws IOException if the destination object doesn't exist, or the target object already
     * exists, or other I/O error occurs
     *
     * N.B. must roll back the operation if the transaction is aborted
     */
    void move_(IPhysicalObject to, PhysicalOp op, Trans t)
            throws IOException, SQLException;
}
