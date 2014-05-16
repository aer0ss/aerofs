/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.persistence;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.dryad.DryadProperties;
import com.aerofs.lib.FileUtil;
import com.google.common.io.ByteStreams;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static java.lang.String.format;

/**
 * N.B. This class is _not_ thread-safe!
 * TODO (AT): improve this to better handle concurrent requests!
 */
@Singleton
public class LocalFileBasedPersistence implements IDryadPersistence
{
    private final String _storageDirectory;

    @Inject
    public LocalFileBasedPersistence(DryadProperties properties)
    {
        _storageDirectory = properties.getProperty(DryadProperties.STORAGE_DIRECTORY);
    }

    @Override
    public void putClientLogs(long customerID, UniqueID dryadID, UserID userID, DID deviceID,
            InputStream src) throws Exception
    {
        File dest = new File(format("%s/%s/%s/client/%s/%s/logs.zip", _storageDirectory,
                customerID, dryadID.toStringFormal(), userID.getString(),
                deviceID.toStringFormal()));

        putLogs(src, dest);
    }

    @Override
    public void putApplianceLogs(long customerID, UniqueID dryadID, InputStream src) throws Exception
    {
        File dest = new File(format("%s/%s/%s/appliance/logs.zip", _storageDirectory, customerID,
                dryadID.toStringFormal()));

        putLogs(src, dest);
    }

    private void putLogs(InputStream src, File dest)
            throws IOException
    {
        // TODO (AT): this is a race condition between workers to access the file system and
        // Java's mkdirs() is _not_ thread safe!
        // See http://www.jroller.com/ethdsy/entry/file_mkdirs_is_not_thread and
        // http://webcache.googleusercontent.com/search?q=cache:VaYghtO8I3gJ:bugs.java.com/view_bug.do%3Fbug_id%3D4742723+&cd=1&hl=en&ct=clnk&gl=us
        FileUtil.ensureDirExists(dest.getParentFile());

        // don't allow overwrites!
        if (dest.exists()) throw new IOException("Destination file already exists!");

        FileOutputStream os = null;
        try {
            // open in append mode so we don't overwrite the file with just the latest chunk.
            os = new FileOutputStream(dest);

            // TODO (AT): a better way to handle the following problem:
            // Multiple requests are handled concurrently. In theory, if the clients are
            // misbehaving, we could be serving multiple requests accessing the same file at the
            // same time.
            // Currently, each client will only update its own resource one chunk at a time, so
            // this problem is avoided. But I prefer to have the server to guard against
            // misbehaving clients.
            ByteStreams.copy(src, os);
        } finally {
            if (os != null) { os.close(); }
        }
    }
}
