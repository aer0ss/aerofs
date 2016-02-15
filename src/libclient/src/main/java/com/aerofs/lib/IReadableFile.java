/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.lib;

import java.io.IOException;
import java.io.InputStream;

public interface IReadableFile
{
    InputStream newInputStream() throws IOException;

    long lengthOrZeroIfNotFile();
    long lastModified() throws IOException;

    boolean wasModifiedSince(long mtime, long fileLength) throws IOException;
}
