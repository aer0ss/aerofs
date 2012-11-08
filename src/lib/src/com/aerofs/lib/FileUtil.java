package com.aerofs.lib;

import com.aerofs.lib.os.OSUtilWindows;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FileUtil
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

    private FileUtil() {}

    private static Set<String> _filesToDelete = Sets.newLinkedHashSet();
    private static FrequentDefectSender _defectSender = new FrequentDefectSender();

    /**
     * Annotate filename with extra infos to make defect reports more informative
     */
    public static String debugString(File f)
    {
        String attrParent = getDebuggingAttributesString(f.getParentFile());
        String attrSelf = getDebuggingAttributesString(f);

        // Do not obfuscate the path since the string may be displayed to the user as error messages
        return f.getAbsolutePath() + " (attributes: " + attrParent + "," + attrSelf + ")";
    }

    private static String getDebuggingAttributesString(File f)
    {
        return  (f.exists() ? "e" : "-") +
                (f.isDirectory() ? "d" : (f.isFile() ? "f" : "-")) +
                (f.canRead() ? "r" : "-") +
                (f.canWrite() ? "w" : "-") +
                (f.canExecute() ? "x" : "-") +
                (CharMatcher.ASCII.matchesAllOf(f.getAbsolutePath()) ? "a" : "-") +
                (OSUtilWindows.isValidFileName(f.getName()) ? "v" : "-");
    }

    /**
     * Called as a Runtime shutdown hook
     */
    private static synchronized void deleteFiles()
    {
        Collection<String> toDelete = _filesToDelete;
        _filesToDelete = null;
        List<String> files = Lists.newArrayList(toDelete);
        Collections.reverse(files);
        for (String path : files) {
            new File(path).delete();
        }
    }

    public static synchronized void deleteOnExit(File file)
    {
        if (_filesToDelete.isEmpty()) {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run()
                {
                    deleteFiles();
                }
            }, "file-deleter"));
        }
        _filesToDelete.add(file.getPath());
    }

    public static synchronized void noDeleteOnExit(File file)
    {
        _filesToDelete.remove(file);
    }

    public static void deleteNow(File file) throws IOException
    {
        if (!tryDeleteNow(file)) throw new IOException("Error deleting " + file);
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

    public static void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) throw new IOException("Could not delete " + debugString(file));
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
            @Nullable File baseDir, boolean deleteOnExit) throws IOException
    {
        if (suffix == null) suffix = ".tmp";
        if (baseDir == null) baseDir = getJavaTempDir();
        String baseName = prefix + System.currentTimeMillis() + "-";
        for (int counter = 0; counter < TEMP_NAME_ATTEMPTS; counter++) {
            File temp = new File(baseDir, baseName + counter + suffix);
            if (deleteOnExit) deleteOnExit(temp);
            if (temp.createNewFile()) return temp;
            if (deleteOnExit) noDeleteOnExit(temp);
        }
        throw new IOException("Failed to create file within " + TEMP_NAME_ATTEMPTS
                + " attempts (tried " + baseName + "{0..." + (TEMP_NAME_ATTEMPTS - 1) + '}'
                + suffix + ')');
    }

    public static void rename(File from, File to) throws IOException
    {
        if (!from.renameTo(to)) {
            throw new IOException("Couldn't rename " + debugString(from)
                    + " to " + debugString(to));
        }
    }

    public static void delete(File file) throws IOException
    {
        if (!file.delete()) throw new IOException("Couldn't delete: " + debugString(file));
    }

    public static void mkdir(File dir) throws IOException
    {
        if (!dir.mkdir()) throw new IOException("Couldn't mkdir: " + debugString(dir));
    }

    public static void mkdirs(File dir) throws IOException
    {
        if (!dir.mkdirs()) throw new IOException("Couldn't mkdirs: " + debugString(dir));
    }

    public static File ensureDirExists(File dir) throws IOException
    {
        if (!dir.isDirectory()) {
            if (!dir.mkdirs()) {
                if (!dir.isDirectory()) {
                    throw new IOException("Couldn't mkdir: " + debugString(dir));
                }
            }
        }
        return dir;
    }

    /**
     * @throws IOException if {@code f} is not a file or doesn't exist.
     */
    private static void throwIfNotFile(File f) throws IOException
    {
        if (!f.isFile()) throw new IOException(f + " is not a file");
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
     * @throw IOException if {@code f} is not a file or doesn't exist.
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
        if (!f.createNewFile()) throw new IOException("couldn't create " + f);
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
            throw new IOException("couldn't rename " + debugString(from)
                    + " to " + debugString(to));
        }
    }

    /**
     * Equivalent to a copy+delete sequence with optimized code path when source and
     * destination reside on same filesystem.
     *
     * Destination will silently be overwritten if it exists.
     */
    public static void moveInOrAcrossFileSystem(File from, File to)
            throws IOException
    {
        if (!from.renameTo(to)) {
            try {
                 copy(from, to, false, false);
            } catch (IOException e) {
                throw new IOException("couldn't rename " + debugString(from)
                        + " to " + debugString(to), e);
            }
            deleteOrOnExit(from);
        }
    }

    public static void setLastModified(File f, long mtime) throws IOException
    {
        throwIfNotFile(f);
        if (!f.setLastModified(mtime)) {
            throw new IOException("can't set mtime for " + debugString(f));
        }
    }

    /**
     * Delete a file. Throw if deletion failed and the file still exists.
     */
    public static void deleteOrThrowIfExist(File f) throws IOException
    {
        if (!f.delete() && f.exists()) {
            throw new IOException("couldn't delete " + debugString(f));
        }
    }

    /**
     * Delete a file recursively. Throw if deletion failed and the file still exists.
     */
    public static void deleteOrThrowIfExistRecursively(File f)
            throws IOException
    {
        File[] children = f.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteOrThrowIfExistRecursively(child);
            }
        }

        // The directory is now empty so delete it
        deleteOrThrowIfExist(f);
    }

    /**
     *  @param keepMTime whether to preserve mtime
     */
    public static void copy(File from, File to, boolean exclusive, boolean keepMTime)
            throws IOException
    {
        if (exclusive && !to.createNewFile()) {
            throw new IOException("the file already exists");
        }

        FileChannel in = null;
        FileChannel out = null;
        try {
            in = new FileInputStream(from).getChannel();
            out = new FileOutputStream(to).getChannel();
            out.transferFrom(in, 0, in.size());

        } finally {
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
        // if directory is changed to a different file midway list will return null,
        // avoids nullpointer exception when iterating over children
        String[] children = from.list();
        if (children != null) {
            if (!to.mkdir() && exclusive) {
                // mkdir may also fail if it already exists
                throw new IOException("cannot create " + to.getPath() +
                        ". it might already exist");
            }

            for (int i = 0; i < children.length; i++) {
                File fChildFrom = new File(from, children[i]);
                File fChildTo = new File(to, children[i]);
                copyRecursively(fChildFrom, fChildTo, exclusive, keepMTime);
            }
        } else {
            copy(from, to, exclusive, keepMTime);
        }
    }

    /**
     * Delete the file recursively. Return false on failure
     */
    public static boolean deleteIgnoreErrorRecursively(File f)
    {
        File[] children = f.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!deleteIgnoreErrorRecursively(child)) return false;
            }
        }

        return f.delete();
    }

    public static void deleteOrOnExit(File f)
    {
        if (!f.delete()) deleteOnExit(f);
    }

    // TODO (MJ) The following *IfNotNFC(...) methods barely belong in this class.
    // Once we determine what we're doing about (and handle) Normalization for string compares,
    // they can be moved or removed and replace with definitive asserts.
    public static void logIfNotNFC(String name, String extraLogs)
    {
        reportIfNotNFC(name, extraLogs, true);
    }

    public static void assertIfNotNFC(String name, String extraLogs)
    {
        reportIfNotNFC(name, extraLogs, false);
    }

    private static void reportIfNotNFC(String name, String extraLogs, boolean shouldLogOnly)
    {
        if (!Normalizer.isNormalized(name, Form.NFC)) {
            final String msg = Joiner.on(' ').useForNull("null").join(
                    "Not NFC:",
                    Util.hexEncode(Util.string2utf(name)),
                    extraLogs);

            if (shouldLogOnly) _defectSender.logSendAsync(msg);
            else assert false : msg;
        }
    }
}
