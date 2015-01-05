/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.UUID;

import static com.aerofs.dryad.DryadProperties.*;
import static com.aerofs.dryad.FileUtils.ensureDirExists;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.nio.channels.Channels.newChannel;

@Singleton
public class LogStore
{
    private static final Logger l = LoggerFactory.getLogger(LogStore.class);

    private final String _storageDirectory;

    @Inject
    public LogStore(DryadProperties properties)
            throws IOException
    {
        _storageDirectory = properties.getProperty(STORAGE_DIRECTORY);

        l.info("Initializing LogStore...");

        for (String dirname : newArrayList(DIR_DEFECTS, DIR_ARCHIVED)) {
            File dir = new File(_storageDirectory, dirname);
            l.info("Creating directory {}", dir.getAbsolutePath());
            ensureDirExists(dir);
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
        ensureDirExists(dest.getParentFile(), 3);

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

    /**
     * This method performs a health check by creating logs under the storage directory and then
     * verify the content of the log created. By doing so, we can be sure that file persistence is
     * working as intended.
     *
     * @throws Exception if the health check fails.
     */
    public void throwIfNotHealthy()
            throws Exception
    {
        l.debug("Performing health checks.");

        String id = UUID.randomUUID().toString().replaceAll("-", "");
        String content = "id: " + id;
        Charset charset = Charset.defaultCharset();

        for (String dir : newArrayList(DIR_DEFECTS, DIR_ARCHIVED)) {
            String path = format("%s/%s/%s", dir, DIR_HEALTHCHECK, id);
            File file = new File(_storageDirectory, path);

            ensureDirExists(file.getParentFile(), 3);
            Files.write(content, file, charset);

            String actual = Files.readFirstLine(file, charset);
            if (!content.equals(actual)) {
                String message = format("Health check failed.\n" +
                        "Expected content: %s\n" +
                        "Actual content: %s", content, actual);
                throw new Exception(message);
            }
        }

        l.debug("Health check succeeded.");
    }
}
