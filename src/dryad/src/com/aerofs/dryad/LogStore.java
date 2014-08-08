/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad;

import com.aerofs.lib.FileUtil;
import com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

import static java.nio.channels.Channels.newChannel;

@Singleton
public class LogStore
{
    public static final String DIR_DEFECTS = "defects";
    public static final String DIR_ARCHIVED = "archived";
    private static final String[] SUB_DIRS = { DIR_DEFECTS, DIR_ARCHIVED };

    private final Logger l = LoggerFactory.getLogger(LogStore.class);

    private final String _storageDirectory;

    @Inject
    public LogStore(DryadProperties properties)
            throws IOException
    {
        _storageDirectory = properties.getProperty(DryadProperties.STORAGE_DIRECTORY);

        l.info("Initializing LogStore...");

        for (String dirname : SUB_DIRS) {
            File dir = new File(_storageDirectory, dirname);
            l.info("Creating directory {}", dir.getAbsolutePath());
            FileUtil.ensureDirExists(dir);
        }
    }

    /**
     * @param filePath - expecting DIR_DEFECTS|DIR_ARCHIVED/.../path_to_file
     */
    public void storeLogs(InputStream src, String filePath)
            throws IOException
    {
        File dest = new File(_storageDirectory, filePath);

        l.debug("Saving to {}", dest.getAbsolutePath());

        // retry 3 times before concluding this is a persistent failure
        FileUtil.ensureDirExists(dest.getParentFile(), 3);

        // FIXME (AT): consider/implement an alternative where we always write to new files
        //   and never override existing files. So when there are concurrent update, each
        //   request will write to a different file. This means we no longer need to serialize
        //   update or use file locks.
        // NOTE: if the above is implemented, we'll need to change the HTTP method to POST since
        //   the request is no longer idempotent.
        FileChannel out = null;
        try {
            out = new FileOutputStream(dest).getChannel();
            // serialize concurrent updates using file locks
            // since we are running in the same JVM, this should work
            out.lock();

            ByteStreams.copy(newChannel(src), out);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}
