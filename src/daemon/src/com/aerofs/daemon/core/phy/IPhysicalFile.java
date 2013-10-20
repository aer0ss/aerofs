package com.aerofs.daemon.core.phy;

import java.io.IOException;
import java.io.InputStream;

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

    InputStream newInputStream_() throws IOException;
}
