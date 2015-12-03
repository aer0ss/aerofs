package com.aerofs.lib;

import com.aerofs.base.Loggers;
import com.aerofs.lib.ex.ExFileIO;
import com.aerofs.lib.ex.ExFileNotFound;
import com.aerofs.lib.os.OSUtilWindows;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class FileUtil
{
    public static class FileName
    {
        public String base;
        public String extension;

        private FileName() {}

        /**
         * Splits a file name into name and extension
         * Ensures that the name part is not empty
         * This is important to correctly append information to the name
         * e.g: so that a file named ".test" gets renamed to ".test (2)" rather than " (2).test"
         *
         * Sample output:
         * given "abc.def", returns "abc" and ".def"
         * given "abcdef", returns "abcdef" and ""
         * given ".def", returns ".def" and ""
         */
        public static FileName fromBaseName(String name)
        {
            FileName result = new FileName();

            int dot = name.lastIndexOf('.');
            if  (dot < 1) {
                result.base = name;
                result.extension = "";
            } else {
                result.base = name.substring(0, dot);
                result.extension = name.substring(dot);
            }

            return result;
        }
    }

    private FileUtil()
    {
        // private to enforce uninstantiability
    }

    private static boolean shutdownHookAdded = false;
    private static Set<File> _filesToDelete = Sets.newLinkedHashSet();
    private static final Logger l = Loggers.getLogger(FileUtil.class);

    /**
     * Returns a string of characters whose positions and values represent attributes of
     * the specified file. For example, a file which exists on the system, is a directory,
     * and can be read and executed will look like:
     * <pre>
     *      edr-x--
     * </pre>
     * @param f the file whose attributes to format
     * @return the formatted attribute string
     */
    public static String getDebuggingAttributesString(File f)
    {
        return  (f.exists() ? "e" : "-") +
                (f.isDirectory() ? "d" : (f.isFile() ? "f" : "-")) +
                (f.canRead() ? "r" : "-") +
                (f.canWrite() ? "w" : "-") +
                (f.canExecute() ? "x" : "-") +
                (CharMatcher.ASCII.matchesAllOf(f.getAbsolutePath()) ? "a" : "-") +
                (OSUtilWindows.isInvalidWin32FileName(f.getName()) ? "-" : "v");
    }

    /**
     * Called as a Runtime shutdown hook
     */
    private static synchronized void deleteFiles()
    {
        Collection<File> toDelete = _filesToDelete;
        _filesToDelete = null;
        List<File> files = Lists.newArrayList(toDelete);
        Collections.reverse(files);
        for (File file : files) {
            file.delete();
        }
    }

    public static synchronized void deleteOnExit(File file)
    {
        if (!shutdownHookAdded) {
            shutdownHookAdded = true;
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run()
                {
                    deleteFiles();
                }
            }, "del-file"));
        }
        _filesToDelete.add(file);
    }

    public static synchronized void noDeleteOnExit(File file)
    {
        _filesToDelete.remove(file);
    }

    public static void deleteNow(File file) throws IOException
    {
        if (!tryDeleteNow(file)) throw new ExFileIO("error deleting file", file);
    }

    public static boolean tryDeleteNow(File file)
    {
        // There's kind of a race condition here:
        // - this thread deletes file
        // - some other thread creates a file with the same name
        // - other thread calls deleteOnExit(file)
        // - this thread calls noDeleteOnExit(file)
        // Making this synchronized would fix that, but I don't want to
        // hold a lock while doing a possibly lengthy deletion on the file
        // system. Since it's unlikely to happen and it's in a temp dir
        // anyways I'm leaving it as-is.
        boolean deleted = file.delete();
        if (deleted) noDeleteOnExit(file);
        return deleted;
    }

    public static void deleteRecursively(File file, @Nullable ProgressIndicators pi) throws IOException
    {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child, pi);
            }
        }
        if (!file.delete()) throw new ExFileIO("could not delete file", file);
        if (pi != null) pi.incrementMonotonicProgress();
    }

    private static File _javaTempDir;

    public static synchronized File getJavaTempDir()
    {
        if (_javaTempDir == null) {
            _javaTempDir = new File(System.getProperty("java.io.tmpdir"));
        }
        return _javaTempDir;
    }

    private static final int TEMP_NAME_ATTEMPTS = 10000;

    public static File createTempDir(String prefix, @Nullable String suffix,
            @Nullable File baseDir, boolean deleteOnExit)
            throws IOException
    {
        if (suffix == null) suffix = ".tmp";
        if (baseDir == null) baseDir = getJavaTempDir();
        String baseName = prefix + System.currentTimeMillis() + "-";
        for (int counter = 0; counter < TEMP_NAME_ATTEMPTS; counter++) {
            File temp = new File(baseDir, baseName + counter + suffix);
            if (deleteOnExit) deleteOnExit(temp);
            if (temp.mkdir()) return temp;
            if (deleteOnExit) noDeleteOnExit(temp);
        }
        throw new IOException("Failed to create directory within " + TEMP_NAME_ATTEMPTS
                + " attempts (tried " + baseName + "{0..." + (TEMP_NAME_ATTEMPTS - 1) + '}'
                + suffix + ')');
    }

    public static File createTempFile(@Nullable String prefix,  @Nullable String suffix,
            @Nullable File baseDir) throws IOException
    {
        if (suffix == null) suffix = ".tmp";
        if (baseDir == null) baseDir = getJavaTempDir();
        String baseName = prefix + System.currentTimeMillis() + "-";
        for (int counter = 0; counter < TEMP_NAME_ATTEMPTS; counter++) {
            File temp = new File(baseDir, baseName + counter + suffix);
            if (temp.createNewFile()) return temp;
        }
        throw new IOException("Failed to create file within " + TEMP_NAME_ATTEMPTS
                + " attempts (tried " + baseName + "{0..." + (TEMP_NAME_ATTEMPTS - 1) + '}'
                + suffix + ')');
    }

    public static void rename(File from, File to) throws IOException
    {
        if (!from.renameTo(to)) {
            throw new ExFileIO("couldn't rename {} to {}", from, to);
        }
    }

    public static void delete(File file) throws IOException
    {
        if (!file.delete()) {
            final String prefix = "couldn't delete file {}";

            // Throw file- or directory-specific exception
            @Nullable File[] children = file.listFiles();
            if (children == null || children.length == 0) throw new ExFileIO(prefix, file);
            else {
                final String suffix = " w children";
                throw new ExFileIO(prefix + suffix, ImmutableList.<File>builder().add(file)
                        .addAll(Arrays.asList(children)).build());
            }
        }
    }

    public static void mkdir(File dir) throws IOException
    {
        if (!dir.mkdir()) throw new ExFileIO("couldn't make the directory", dir);
    }

    /**
     * This is duplicated in FileUtils#mkdirs() in the dryad module.
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
            throw new ExFileIO("couldn't make the directories", dir);
        }
    }

    /**
     * Java's mkdirs() is _not_ thread safe!
     * See http://www.jroller.com/ethdsy/entry/file_mkdirs_is_not_thread and
     * http://bugs.java.com/view_bug.do?bug_id=4742723
     *
     * Consider using ensureDirExists(dir, retryAttempts) in multi-threaded environment.
     *
     * This is duplicated in FileUtils#ensureDirExists() in the dryad module.
     */
    public static File ensureDirExists(File dir) throws IOException
    {
        if (!dir.isDirectory()) mkdirs(dir);
        return dir;
    }

    /**
     * retry mkdirs() {@paramref retryAttempts} times because mkdirs() is not thread safe and
     * may fail when there are concurrent mkdirs()
     *
     * This is duplicated in FileUtils#ensureDirExists() in the dryad module.
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
     * @throws IOException if {@code f} is not a file or doesn't exist.
     */
    private static void throwIfNotFile(File f) throws IOException
    {
        if (!f.exists()) throw new ExFileNotFound(f);
        if (!f.isFile()) throw new ExFileIO("{} is not a file", f);
    }

    /**
     * @throws IOException if {@code f} is not a file or doesn't exist.
     */
    public static long getLength(File f) throws IOException
    {
        throwIfNotFile(f);
        return f.length();
    }

    /**
     * @return zero if {@code f} is not a file or doesn't exist.
     */
    public static long getLengthOrZeroIfNotFile(File f)
    {
        // We could return f.length() without checking. However, its behavior is unspecified if
        // f designates a folder.
        return f.isFile() ? f.length() : 0;
    }

    /**
     * @return the mtime of a file, if the mtime is negative then return 0.
     * @throws IOException if {@code f} is not a file or doesn't exist.
     */
    public static long lastModified(File f) throws IOException
    {
        throwIfNotFile(f);

        // set mtime to be 0 in case the modification time is before epoch.
        long mtime = f.lastModified();
        return mtime < 0 ? 0 : mtime;
    }

    /**
     * @return whether the modification time and file length is different from the given values
     * @throws IOException if {@code f} is not a file or doesn't exist.
     */
    public static boolean wasModifiedSince(File f, long mtime, long len)
            throws IOException
    {
        throwIfNotFile(f);
        long lenNow = f.length();
        long mtimeNow = lastModified(f);
        return mtimeNow != mtime || lenNow != len;
    }

    public static void createNewFile(File f) throws IOException
    {
        if (!f.createNewFile()) throw new ExFileIO("couldn't create file", f);
    }

    /**
     * N.B.: the to file is silently overwritten if any.
     *
     * use copy() + delete*() or OSUtil.moveInOrAcrossFileSystem for cross-fs moves
     */
    public static void moveInSameFileSystem(File from, File to)
            throws IOException
    {
        if (!from.renameTo(to)) {
            throw new ExFileIO("couldn't rename {} to {}", from, to);
        }
    }

    public static void moveInOrAcrossFileSystem(File from, File to) throws IOException
    {
        moveInOrAcrossFileSystem(from, to, null);
    }

    /**
     * Equivalent to a copy+delete sequence with optimized code path when source and
     * destination reside on same filesystem.
     *
     * Destination will silently be overwritten if it exists.
     */
    public static void moveInOrAcrossFileSystem(File from, File to, @Nullable ProgressIndicators pi)
            throws IOException
    {
        if (!from.renameTo(to)) {
            try {
                copy(from, to, false, true, pi);
            } catch (IOException e) {
                throw new ExFileIO("couldn't rename {} to {}", from, to);
            }
            deleteOrOnExit(from);
        }
    }

    public static void setLastModified(File f, long mtime) throws IOException
    {
        throwIfNotFile(f);
        if (!f.setLastModified(mtime)) {
            throw new ExFileIO("can't set the last modified time " + mtime + " for file", f);
        }
    }

    /**
     * Delete a file. Throw if deletion failed and the file still exists.
     */
    public static void deleteOrThrowIfExist(File f) throws IOException
    {
        if (!f.delete() && f.exists()) {
            String[] children = f.list();
            if (children != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("couldn't delete folder {} children: ");
                for (String child : children) {
                     sb.append(Util.crc32(child));
                     sb.append(',');
                }
                throw new ExFileIO(sb.toString(), f);
            } else {
                throw new ExFileIO("couldn't delete file {}", f);
            }
        }
    }
    public static void deleteOrThrowIfExistRecursively(File f) throws IOException
    {
        deleteOrThrowIfExistRecursively(f, null);
    }

    /**
     * Delete a file recursively. Throw if deletion failed and the file still exists.
     */
    public static void deleteOrThrowIfExistRecursively(File f, @Nullable ProgressIndicators pi)
            throws IOException
    {
        File[] children = f.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteOrThrowIfExistRecursively(child, pi);
            }
        }

        // The directory is now empty so delete it
        deleteOrThrowIfExist(f);
        if (pi != null) pi.incrementMonotonicProgress();
    }

    public static void copy(File from, File to, boolean exclusive, boolean keepMTime)
            throws IOException
    {
        copy(from, to, exclusive, keepMTime, null);
    }

    /**
     *  @param keepMTime whether to preserve mtime
     */
    public static void copy(File from, File to, boolean exclusive, boolean keepMTime,
            @Nullable ProgressIndicators pi) throws IOException
    {
        if (from.getAbsolutePath().equals(to.getAbsolutePath())) return;
        if (exclusive && !to.createNewFile()) {
            throw new ExFileIO("file {} already exists", to);
        }

        FileChannel in = null;
        FileChannel out = null;
        try {
            in = new FileInputStream(from).getChannel();
            out = new FileOutputStream(to).getChannel();
            if (pi != null) pi.startSyscall();
            out.transferFrom(in, 0, in.size());
        } finally {
            if (pi != null) pi.endSyscall();
            if (in != null) in.close();
            if (out != null) out.close();
        }

        // MTime can not be modified if the FileChannel has not been closed.
        if (keepMTime) {
            setLastModified(to, lastModified(from));
        }
    }

    public static void copyRecursively(File from, File to, boolean exclusive, boolean keepMTime)
            throws IOException
    {
        copyRecursively(from, to, exclusive, keepMTime, null);
    }

    public static void copyRecursively(File from, File to, boolean exclusive, boolean keepMTime,
            @Nullable ProgressIndicators pi) throws IOException
    {
        // if directory is changed to a different file midway list will return null,
        // avoids nullpointer exception when iterating over children
        String[] children = from.list();
        if (children != null) {
            if (!to.mkdir() && exclusive) {
                // mkdir may also fail if it already exists
                throw new ExFileIO("cannot create file {}. it might already exist", to);
            }

            for (String child : children) {
                File fChildFrom = new File(from, child);
                File fChildTo = new File(to, child);
                copyRecursively(fChildFrom, fChildTo, exclusive, keepMTime, pi);
            }
        } else {
            copy(from, to, exclusive, keepMTime, pi);
        }
        if (pi != null) pi.incrementMonotonicProgress();
    }

    public static boolean deleteIgnoreErrorRecursively(File f)
    {
        return deleteIgnoreErrorRecursively(f, null);
    }

    /**
     * Delete the file recursively. Return false on failure
     */
    public static boolean deleteIgnoreErrorRecursively(File f, @Nullable ProgressIndicators pi)
    {
        File[] children = f.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!deleteIgnoreErrorRecursively(child, pi)) return false;
            }
        }

        boolean ok = f.delete();
        if (pi != null) pi.incrementMonotonicProgress();
        return ok;
    }

    /**
     * Delete the file recursively. If we failed to delete a file, we retain the File object and
     * add a hook (if not already added) to try again just before JVM shuts down.
     *
     * N.B. This method may cause big memory footprint on large directory trees, so please use this
     * method carefully.
     *
     * For example, take a directory with 10k files. Assuming it's a directory, then we are looking
     * at files with long absolute paths so let's just assume 30 characters per file on average.
     * Then we are looking at 30b / file * 10k files => 300kb memory hogged up until the user shuts
     * down the program.
     */
    public static void deleteRecursivelyOrOnExit(File f)
    {
        File[] children = f.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursivelyOrOnExit(child);
            }
        }

        deleteOrOnExit(f);
    }

    public static void deleteOrOnExit(File f)
    {
        // If we fail to delete the file, and the file still exists (rather than failing because
        // e.g. the file was already deleted), then flag the file to be deleted at JVM shutdown
        if (!f.delete() && f.exists()) deleteOnExit(f);
    }
}
