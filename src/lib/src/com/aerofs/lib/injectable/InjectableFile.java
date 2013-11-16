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
import java.io.OutputStream;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.List;

import javax.annotation.Nullable;

import com.aerofs.lib.FileUtil;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtil.OSFamily;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

/**
 * A wrapper class of java.io.File for common file operations. This class also
 * makes file objects compatible with dependency injection.
 */
public class InjectableFile
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
    public long lastModified() throws IOException
    {
        return FileUtil.lastModified(_f);
    }

    public boolean isFile()
    {
        return _f.isFile();
    }

    public boolean isDirectory()
    {
        return _f.isDirectory();
    }

    public boolean exists()
    {
        return _f.exists();
    }

    public long getLength() throws IOException
    {
        return FileUtil.getLength(_f);
    }

    public long getLengthOrZeroIfNotFile()
    {
        return FileUtil.getLengthOrZeroIfNotFile(_f);
    }

    public boolean wasModifiedSince(long mtime, long len) throws IOException
    {
        return FileUtil.wasModifiedSince(_f, mtime, len);
    }

    public void createNewFile() throws IOException
    {
        FileUtil.createNewFile(_f);
    }

    public boolean createNewFileIgnoreError() throws IOException
    {
        return _f.createNewFile();
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
        FileUtil.moveInSameFileSystem(_f, to._f);
    }

    /**
     * Similar to moveInSameFileSystem, but return false instead throwing on
     * errors.
     */
    public boolean moveInSameFileSystemIgnoreError(InjectableFile to)
    {
        return _f.renameTo(to._f);
    }

    /**
     * N.B. this method should be used rarely, as improper use may break DI
     */
    public File getImplementation()
    {
        return _f;
    }

    public void setLastModified(long mtime) throws IOException
    {
        FileUtil.setLastModified(_f, mtime);
    }

    public void mkdir() throws IOException
    {
        FileUtil.mkdir(_f);
    }

    public boolean mkdirIgnoreError()
    {
        return _f.mkdir();
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
        String[] l = _f.list();
        // Since OSX automatically converts files to NFD, but the rest of the world uses NFC,
        // Java on OSX automatically converts file paths to NFC, but we care about the actual
        // representation.  Ideally we'd write our own path handling, but for now, it's safe to
        // just convert all paths to NFD on OSX.
        if (l != null && _factory._osutil.getOSFamily() == OSFamily.OSX) {
            for (int i = 0; i < l.length ; i++) {
                l[i] = Normalizer.normalize(l[i], Form.NFD);
            }
        }
        return l;
    }

    public String getName()
    {
        return _f.getName();
    }

    public String getPath()
    {
        return _f.getPath();
    }

    public void delete() throws IOException
    {
        FileUtil.delete(_f);
    }

    /**
     * Delete a file. Return false on failure
     */
    public boolean deleteIgnoreError()
    {
        return _f.delete();
    }

    public void deleteOrThrowIfExist() throws IOException
    {
        FileUtil.deleteOrThrowIfExist(_f);
    }

    public void deleteOrThrowIfExistRecursively() throws IOException
    {
        FileUtil.deleteOrThrowIfExistRecursively(_f, _factory._pi);
    }

    public void copy(InjectableFile to, boolean exclusive, boolean keepMTime) throws IOException
    {
        FileUtil.copy(_f, to._f, exclusive, keepMTime, _factory._pi);
    }

    public void copyRecursively(InjectableFile to, boolean exclusive, boolean keepMTime)
            throws IOException
    {
        FileUtil.copyRecursively(_f, to._f, exclusive, keepMTime, _factory._pi);
    }

    public boolean deleteIgnoreErrorRecursively()
    {
        return FileUtil.deleteIgnoreErrorRecursively(_f, _factory._pi);
    }

    public void deleteOnExit()
    {
        FileUtil.deleteOnExit(_f);
    }

    /**
     * Delete file, and call deleteOnExit() if deletion failed.
     */
    public void deleteOrOnExit()
    {
        FileUtil.deleteOrOnExit(_f);
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
        FileUtil.mkdirs(_f);
    }

    public void ensureDirExists() throws IOException
    {
        FileUtil.ensureDirExists(_f);
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
        return _f.canRead() &&
                !(_f.isDirectory() && OSUtil.isWindows() && _f.list() == null);
    }

    public boolean canWrite()
    {
        return _f.canWrite();
    }

    public long getTotalSpace()
    {
        return _f.getTotalSpace();
    }

    public long getUsableSpace()
    {
        return _f.getUsableSpace();
    }

    public InputStream newInputStream() throws FileNotFoundException
    {
        return new FileInputStream(_f);
    }

    public OutputStream newOutputStream() throws FileNotFoundException
    {
        return new FileOutputStream(_f);
    }

}
