/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * This class duplicates the selected methods from the same FileUtils class in the lib module.
 *
 * The reason for doing so is because Dryad needs to be compatible with Java 6 for various reasons
 * while the rest of the code base have moved on to Java 8.
 */
public class FileUtils
{
    private static final Logger l = LoggerFactory.getLogger(FileUtils.class);

    /**
     * Java's mkdirs() is _not_ thread safe!
     * See http://www.jroller.com/ethdsy/entry/file_mkdirs_is_not_thread and
     * http://bugs.java.com/view_bug.do?bug_id=4742723
     *
     * Consider using ensureDirExists(dir, retryAttempts) in multi-threaded environment.
     */
    public static File ensureDirExists(File dir) throws IOException
    {
        if (!dir.isDirectory()) mkdirs(dir);
        return dir;
    }

    /**
     * retry mkdirs() {@paramref retryAttempts} times because mkdirs() is not thread safe and
     * may fail when there are concurrent mkdirs()
     */
    public static File ensureDirExists(File dir, int retryAttempts) throws IOException
    {
        for (int i = 1; i <= retryAttempts; i++) {
            try {
                ensureDirExists(dir);
                return dir;
            } catch (IOException e) {
                if (i == retryAttempts) {
                    throw e;
                }
                l.debug("mkdirs failed, trying again...");
            }
        }

        throw new AssertionError("This line should be unreachable.");
    }

    /**
     * This is a duplicate of FileUtil#mkdirs() because Dryad is compiled with Java 6
     * while FileUtil is compiled with Java 8.
     */
    public static void mkdirs(File dir) throws IOException
    {
        // sigh Windows / long path / Java...
        // For some obscure reason the contract of File.mkdirs is not honored on Windows when
        // using the magic prefix required to work with long pathes and filenames w/ trailing
        // spaces/periods. Instead it just behaves like File.mkdir() so we have to manually
        // create the whole missing hierarchy
        if (dir.getPath().startsWith("\\\\?\\")) {
            File p = dir.getParentFile();
            // File.exists always return false for drive roots when using the magic prefix
            if (p != null && !p.getPath().endsWith(":") && !p.exists()) mkdirs(p);
        }
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("couldn't make the directories");
        }
    }
}
