package com.aerofs.daemon.core.phy;

import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.IReadableFile;
import com.aerofs.lib.id.KIndex;

import java.io.IOException;
import java.sql.SQLException;

public interface IPhysicalFile extends IReadableFile, IPhysicalObject
{
    /**
     * @return the absolute path of the physical file in the file system,
     * or null if no such path exists
     */
    String getAbsPath_();

    boolean exists_();

    /**
     * @throws IOException if the destination object doesn't exist, or the target object already
     * exists, or other I/O error occurs
     *
     * N.B. must roll back the operation if the transaction is aborted
     */
    void move_(ResolvedPath to, KIndex kidx, PhysicalOp op, Trans t)
            throws IOException, SQLException;

    void onUnexpectedModification_(long expectedMtime) throws IOException;

    void prepareForAccessWithoutCoreLock_() throws SQLException;
}
