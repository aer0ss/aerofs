/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.store;

import com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

import static com.aerofs.dryad.Constants.STORAGE_SUB_DIRECTORIES;
import static java.nio.channels.Channels.newChannel;

public final class FileStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileStore.class);

    private final String storageDirectory;

    public FileStore(String storageDirectory) throws IOException {
        this.storageDirectory = storageDirectory;

        for (String directoryName : STORAGE_SUB_DIRECTORIES) {
            File directory = new File(storageDirectory, directoryName);
            LOGGER.info("Creating directory {}", directory.getAbsolutePath());
            ensureDirectoryExists(directory);
        }
    }

    /**
     * @param filePath - expecting DEFECTS_DIRECTORY|ARCHIVED_LOGS_DIRECTORY/.../path_to_file
     */
    public void storeLogs(InputStream source, String filePath) throws IOException {
        File destination = new File(storageDirectory, filePath);

        // retry 3 times before concluding this is a persistent failure
        ensureDirectoryExists(destination.getParentFile(), 3);

        // FIXME (AT): consider/implement an alternative where we always write to new files
        //   and never override existing files. So when there are concurrent update, each
        //   request will write to a different file. This means we no longer need to serialize
        //   update or use file locks.
        // NOTE: if the above is implemented, we'll need to change the HTTP method to POST since
        //   the request is no longer idempotent.
        FileChannel out = null;
        try {
            out = new FileOutputStream(destination).getChannel();
            // serialize concurrent updates using file locks
            // since we are running in the same JVM, this should work
            out.lock();

            ByteStreams.copy(newChannel(source), out);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                // noop
            }

            try {
                source.close();
            } catch (IOException e) {
                // noop
            }
        }
    }

    /**
     * Java's makeDirectory() is _not_ thread safe!
     * See http://www.jroller.com/ethdsy/entry/file_mkdirs_is_not_thread and
     * http://bugs.java.com/view_bug.do?bug_id=4742723
     *
     * Consider using ensureDirectoryExists(dir, retryAttempts) in multi-threaded environment.
     */
    public static File ensureDirectoryExists(File directory) throws IOException {
        if (!directory.isDirectory()) {
            makeDirectory(directory);
        }

        return directory;
    }

    /**
     * retry makeDirectory() {@code retryAttempts} times because makeDirectory() is not thread safe and
     * may fail when there are concurrent makeDirectory()
     */
    public static File ensureDirectoryExists(File directory, int retryAttempts) throws IOException {
        for (int i = 1; i <= retryAttempts; i++) {
            try {
                ensureDirectoryExists(directory);
                return directory;
            } catch (IOException e) {
                if (i == retryAttempts) {
                    throw e;
                }

                LOGGER.debug("makeDirectory failed, trying again...");
            }
        }

        throw new IllegalStateException("ensureDirectoryExists completed in bad state");
    }

    public static void makeDirectory(File directory) throws IOException {
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("couldn't make the directories");
        }
    }
}
