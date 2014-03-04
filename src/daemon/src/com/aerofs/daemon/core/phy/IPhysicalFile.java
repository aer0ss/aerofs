package com.aerofs.daemon.core.phy;

import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.KIndex;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

public interface IPhysicalFile extends IPhysicalObject
{
    /**
     * @return 0 if the file doesn't exist
     */
    long getLength_();

    /**
     * @return LocalFile must return the last modification time of the physical
     * file, and throw IOException if the file doesn't exist; other
     * implementations must return the current system time.
     */
    long getLastModificationOrCurrentTime_() throws IOException;

    boolean wasModifiedSince(long mtime, long len) throws IOException;

    /**
     * @return the absolute path of the physical file in the file system,
     * or null if no such path exists
     */
    String getAbsPath_();

    boolean exists_();

    InputStream newInputStream_() throws IOException;

    /**
     * @throws IOException if the destination object doesn't exist, or the target object already
     * exists, or other I/O error occurs
     *
     * N.B. must roll back the operation if the transaction is aborted
     */
    void move_(ResolvedPath to, KIndex kidx, PhysicalOp op, Trans t)
            throws IOException, SQLException;
}
