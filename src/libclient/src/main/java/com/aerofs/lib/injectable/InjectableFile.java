/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.injectable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.nio.file.Files;
import java.util.Set;

import javax.annotation.Nullable;

import com.aerofs.base.Loggers;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.IReadableFile;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtil.OSFamily;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

/**
 * A wrapper class of java.io.File for common file operations. This class also
 * makes file objects compatible with dependency injection.
 */
public class InjectableFile implements IReadableFile
{
    public static class Factory
    {
        private final ProgressIndicators _pi;
        private final IOSUtil _osutil;

        @Inject
        public Factory()
        {
            _pi = ProgressIndicators.get(); // should be injected
            _osutil = OSUtil.get(); // could be injected
        }

        public InjectableFile create(String path)
        {
            return create((InjectableFile)null, path);
        }

        public InjectableFile create(String parent, String name)
        {
            return create(parent == null ? null : create(parent), name);
        }

        public InjectableFile create(InjectableFile parent, String name)
        {
            return wrap(new File(parent == null ? null : parent._f, name));
        }

        public InjectableFile createTempFile(String prefix, String suffix) throws IOException
        {
            return wrap(File.createTempFile(prefix, suffix));
        }

        protected InjectableFile wrap(File file)
        {
            return new InjectableFile(this, file);
        }
    }

    private final Factory _factory;
    private final File _f;

    /**
     * Oh My Goodness this is such a terrible clusterfuck...
     *
     * Windows API enforces plenty of restrictions on what can be a valid filename, except all
     * those restrictions can actually be bypassed by writing directly to the FS from another OS
     * or using a special prefix to force path to be passed as-is to the FS. For instance all
     * traditional UNIX tools provided by Cygwin can manipulate "invalid" filenames without any
     * problem.
     *
     * As a result we may actually run into "invalid" files during a scan. This is a problem because
     * our system expect to be able to do full lossless round-trips for any files it scans. Breaking
     * that assumption would open a nasty can of worms. For this reason the Scanner will actually
     * ignore any "invalid" file, even when presented with material evidence that it can actually
     * exists. This keeps the behavior predictable but can lead to confusion for users so we aim
     * to support anything the underlying FS does.
     *
     * Unfortunately Java does not by default bypass these restrictions, hence the need to do it
     * ourselves. For some obscure reason, adding the magic prefix to filenames from Java-land is
     * not enough to bypass all restrictions. Ideally we'd either fix the JDK (since we ship our
     * own build on Windows) or add more methods to InjectableDriver to ensure we can gracefully
     * handle anything the underlying system supports. In the meantime however this is good enough
     * to support trailing spaces and periods, which have been observed in the wild.
     *
     * NB: not all code may be able to deal with the magic prefix so we need to use a separate
     * File object for all interactions with the FS instead of modifiying the original one.
     */
    private File _prefixed;
    private File winSafe()
    {
        if (_prefixed == null) {
            _prefixed = _factory._osutil.getOSFamily() == OSFamily.WINDOWS
                    && !_f.getAbsolutePath().startsWith("\\")
                    ?  new File("\\\\?\\" + _f.getAbsolutePath())
                    : _f;
        }
        return _prefixed;
    }

    private InjectableFile(Factory factory, File f)
    {
        _factory = factory;
        _f = f;
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && ((InjectableFile) o)._f.equals(_f));
    }

    @Override
    public int hashCode()
    {
        return _f.hashCode();
    }

    @Override
    public String toString()
    {
        return _f.toString();
    }

    public InjectableFile.Factory getFactory()
    {
        return _factory;
    }

    public InjectableFile newChild(String child)
    {
        return _factory.create(this, child);
    }

    public InjectableFile createTempFile(String prefix, String suffix) throws IOException
    {
        return _factory.wrap(File.createTempFile(prefix, suffix, _f));
    }

    /**
     * @return the mtime of a file. the file must not be a folder.
     */
    @Override
    public long lastModified() throws IOException
    {
        return FileUtil.lastModified(winSafe());
    }

    public boolean isFile()
    {
        return winSafe().isFile();
    }

    public boolean isDirectory()
    {
        return winSafe().isDirectory();
    }

    public boolean exists()
    {
        return winSafe().exists();
    }

    /**
     * Directories to be sync are expected to be both readable and writable
     */
    public void fixDirectoryPermissions() throws IOException
    {
        if (_factory._osutil.getOSFamily() != OSFamily.WINDOWS) {
            try {
                Path f = _f.toPath();
                Set<PosixFilePermission> p = Files.getPosixFilePermissions(f, LinkOption.NOFOLLOW_LINKS);
                p.add(PosixFilePermission.OWNER_READ);
                p.add(PosixFilePermission.OWNER_WRITE);
                p.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(f, p);
                return;
            } catch (UnsupportedOperationException e) {}
        }
        File w = winSafe();
        w.setReadable(true);
        w.setWritable(true);
        w.setExecutable(true);
    }

    public void fixFilePermissions() throws IOException
    {
        if (_factory._osutil.getOSFamily() != OSFamily.WINDOWS) {
            try {
                Path f = _f.toPath();
                Set<PosixFilePermission> p = Files.getPosixFilePermissions(f, LinkOption.NOFOLLOW_LINKS);
                p.add(PosixFilePermission.OWNER_READ);
                p.add(PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(f, p);
                return;
            } catch (UnsupportedOperationException e) {}
        }
        File w = winSafe();
        w.setReadable(true);
        w.setWritable(true);
    }

    public long length() throws IOException
    {
        return FileUtil.getLength(winSafe());
    }

    @Override
    public long lengthOrZeroIfNotFile()
    {
        return FileUtil.getLengthOrZeroIfNotFile(winSafe());
    }

    @Override
    public boolean wasModifiedSince(long mtime, long len) throws IOException
    {
        return FileUtil.wasModifiedSince(winSafe(), mtime, len);
    }

    public void createNewFile() throws IOException
    {
        FileUtil.createNewFile(winSafe());
    }

    public boolean createNewFileIgnoreError() throws IOException
    {
        return winSafe().createNewFile();
    }

    public String getAbsolutePath()
    {
        return _f.getAbsolutePath();
    }

    public String getCanonicalPath() throws IOException
    {
        return _f.getCanonicalPath();
    }

    public void moveInSameFileSystem(InjectableFile to) throws IOException
    {
        // This is really fucking gross but sometimes Windows is just being
        // terribly terribly annoying and rename fail for no apparent reason,
        // presumably because some other application (antivirus for instance)
        // is watching file changes and temporarily holding a file lock at
        // just the wrong time.
        // Retrying the operation a few times should hopefully mask that
        // stupid behavior and prevent the mistaken creation of CNROs
        if (_factory._osutil.getOSFamily() == OSFamily.WINDOWS) {
            for (int i = 0; i < 4; ++i) {
                try {
                    FileUtil.moveInSameFileSystem(winSafe(), to.winSafe());
                    return;
                } catch (IOException e) {
                    // don't retry if there's a good reason for the move to fail
                    if (!exists() || to.exists()) throw e;
                }
                Loggers.getLogger(InjectableFile.class).info("retry rename {} {}", this, to);
                try {
                    Thread.sleep(5L);
                } catch (InterruptedException e) {}
            }
        }
        FileUtil.moveInSameFileSystem(winSafe(), to.winSafe());
    }

    /**
     * Similar to moveInSameFileSystem, but return false instead throwing on
     * errors.
     */
    public boolean moveInSameFileSystemIgnoreError(InjectableFile to)
    {
        return winSafe().renameTo(to.winSafe());
    }

    /**
     * N.B. this method should be used rarely, as improper use may break DI
     */
    public File getImplementation()
    {
        return winSafe();
    }

    public void setLastModified(long mtime) throws IOException
    {
        FileUtil.setLastModified(winSafe(), mtime);
    }

    public void mkdir() throws IOException
    {
        FileUtil.mkdir(winSafe());
    }

    public boolean mkdirIgnoreError()
    {
        return winSafe().mkdir();
    }

    /**
     * @return null if the file is not a folder or doesn't exist
     */
    public @Nullable InjectableFile[] listFiles(@Nullable FilenameFilter filter)
    {
        // Important: don't use _f.listFiles() here, but use InjectableFile.list() to handle OSX
        // NFC/NFD clusterfuck properly.
        String[] children = list();
        if (children == null) return null;
        List<InjectableFile> v = Lists.newArrayList();
        for (String child : children) {
            if (filter == null || filter.accept(_f, child)) {
                v.add(_factory.create(this, child));
            }
        }
        return v.toArray(new InjectableFile[v.size()]);
    }

    /**
     * @return null if the file is not a folder or doesn't exist
     */
    public @Nullable InjectableFile[] listFiles()
    {
        return listFiles(null);
    }

    /**
     * @see File#list()
     */
    public @Nullable String[] list()
    {
        return winSafe().list();
    }

    public String getName()
    {
        return _f.getName();
    }

    public String getPath()
    {
        return _f.getPath();
    }

    public boolean isSymbolicLink() {
        Path fp = Paths.get(getAbsolutePath());
        return Files.isSymbolicLink(fp);
    }

    public void delete() throws IOException
    {
        FileUtil.delete(winSafe());
    }

    /**
     * Delete a file. Return false on failure
     */
    public boolean deleteIgnoreError()
    {
        return winSafe().delete();
    }

    public void deleteOrThrowIfExist() throws IOException
    {
        FileUtil.deleteOrThrowIfExist(winSafe());
    }

    public void deleteOrThrowIfExistRecursively() throws IOException
    {
        FileUtil.deleteOrThrowIfExistRecursively(winSafe(), _factory._pi);
    }

    public void copy(InjectableFile to, boolean exclusive, boolean keepMTime) throws IOException
    {
        FileUtil.copy(winSafe(), to.winSafe(), exclusive, keepMTime, _factory._pi);
    }

    public void copyRecursively(InjectableFile to, boolean exclusive, boolean keepMTime)
            throws IOException
    {
        FileUtil.copyRecursively(winSafe(), to.winSafe(), exclusive, keepMTime, _factory._pi);
    }

    public boolean deleteIgnoreErrorRecursively()
    {
        return FileUtil.deleteIgnoreErrorRecursively(winSafe(), _factory._pi);
    }

    public void deleteOnExit()
    {
        FileUtil.deleteOnExit(winSafe());
    }

    /**
     * Delete file, and call deleteOnExit() if deletion failed.
     */
    public void deleteOrOnExit()
    {
        FileUtil.deleteOrOnExit(winSafe());
    }

    public InjectableFile getParentFile()
    {
        return _factory.create(_f.getParentFile().getPath());
    }

    public String getParent()
    {
        return _f.getParent();
    }

    public void mkdirs() throws IOException
    {
        FileUtil.mkdirs(winSafe());
    }

    public void ensureDirExists() throws IOException
    {
        FileUtil.ensureDirExists(winSafe());
    }

    /**
     * Returns true if the file is readable.
     * NOTE there is additional behavior here for canRead() on directories.
     */
    public boolean canRead()
    {
        /* WARNING fancy boolean logic.
         * Read the following as:
         *  - for files, true if f.canRead()
         *  - for directories on POSIX, true if f.canRead()
         *  - for directories on WIndows, true if f.canRead() and f.list() works normally.
         *
         * FIXME(jP): I don't like using .list() here, but it is the only way to detect
         * a file symlink that points to a directory on Windows. You think that is evil? It is.
         * But it exists in the world.
         * Another way to look at this: if dir.list() returns null, then we don't care WHY,
         * it always means that this directory is unreadable to us.
         */
        return winSafe().canRead() &&
                !(isDirectory() && OSUtil.isWindows() && list() == null);
    }

    public boolean canWrite()
    {
        return winSafe().canWrite();
    }

    public long getTotalSpace()
    {
        return winSafe().getTotalSpace();
    }

    public long getUsableSpace()
    {
        return winSafe().getUsableSpace();
    }

    @Override
    public InputStream newInputStream() throws FileNotFoundException
    {
        return new FileInputStream(winSafe());
    }

    public byte[] toByteArray() throws IOException
    {
        try (InputStream in = newInputStream()) {
            return ByteStreams.toByteArray(in);
        }
    }

    public FileOutputStream newOutputStream() throws FileNotFoundException
    {
        return new FileOutputStream(winSafe());
    }

    public FileOutputStream newOutputStream(boolean append) throws FileNotFoundException
    {
        return new FileOutputStream(winSafe(), append);
    }
}
