/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.cache;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.phy.block.BlockStorageDatabase;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend;
import com.aerofs.daemon.lib.DelayedScheduler;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsDefaultAuxRoot;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Proxy backend adding caching on top of an arbitrary storage backend
 */
public class CacheBackend implements IBlockStorageBackend
{
    static final String TARGET_ANNOTATION = "CacheTarget";

    private final static Logger l = Loggers.getLogger(CacheBackend.class);

    private static int MAX_CACHE_SIZE = 1 * C.KB;

    private static final long SLACK_MILLIS = 1 * C.MIN;
    private static final long SLEEP_MILLIS = 5 * C.MIN;

    private static final double FREE_SPACE_LOW = 0.32;
    private static final double FREE_SPACE_HIGH = 0.36;

    private final CfgAbsDefaultAuxRoot _absAuxRoot;

    private final TransManager _tm;
    private final CoreScheduler _sched;
    private File _cacheDir;
    private File _downloadDir;

    private final CacheDatabase _cdb;
    private final IBlockStorageBackend _bsb;

    /**
     * chunk states:
     *
     * in status map
     * | in file system
     * | |
     *     missing
     * y   downloading
     * y y in use
     *   y cached
     */

    private static class CacheStatus
    {
        int _count;
        Future<File> _future;

        @Override
        public String toString()
        {
            return String.valueOf(_count);
        }
    }

    private final Map<ContentBlockHash, CacheStatus> _statusMap = Maps.newHashMap();

    private final Object _accessTimeSync = new Object();

    private final Cache<ContentBlockHash, Date> _accessTimeCache =
            CacheBuilder.newBuilder().maximumSize(MAX_CACHE_SIZE).build();

    private Map<ContentBlockHash, Date> _accessTimeDirtyMap = Maps.newHashMap();
    private Map<ContentBlockHash, Date> _oldAccessTimeDirtyMap = Maps.newHashMap();

    private final SpaceFreer _spaceFreer = new SpaceFreer();

    @Inject
    public CacheBackend(CfgAbsDefaultAuxRoot absAuxRoot, TransManager tm, CoreScheduler sched,
            CacheDatabase cdb, @Named(TARGET_ANNOTATION) IBlockStorageBackend bsb)
    {
        _absAuxRoot = absAuxRoot;
        _tm = tm;
        _sched = sched;
        _cdb = cdb;
        _bsb = bsb;
    }

    @Override
    public void init_() throws IOException
    {
        _cacheDir = new File(_absAuxRoot.get(), "cache");
        if (!(_cacheDir.isDirectory() || _cacheDir.mkdirs())) {
            throw new IOException("Cannot create cache dir " + _cacheDir.getAbsolutePath());
        }
        _downloadDir = new File(_absAuxRoot.get(), "blockdownload");
        if (!(_downloadDir.isDirectory() || _downloadDir.mkdirs())) {
            throw new IOException("Cannot create cache download dir "
                    + _downloadDir.getAbsolutePath());
        }

        _bsb.init_();

        try {
            // scan over all the files in the cache directory, deleting anything we don't know
            // about in the cache database
            for (File child : listFiles(_cacheDir)) {
                if (!child.isDirectory()) continue;
                for (File file : listFiles(child)) {
                    if (!file.isFile()) {
                        file.delete();
                        continue;
                    }
                    byte[] key = getKeyFromFile(file);
                    if (key == null) {
                        file.delete();
                        continue;
                    }
                    if (l.isDebugEnabled()) l.debug("found block " + BaseUtil.hexEncode(key));
                    long accessTime = _cdb.getCacheAccess(key);
                    if (accessTime <= BlockStorageDatabase.DELETED_FILE_DATE) {
                        file.delete();
                    }
                }
            }
            // delete all files in the cache download directory.  They were incomplete downloads.
            for (File child : listFiles(_downloadDir)) {
                FileUtil.deleteIgnoreErrorRecursively(child);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

        _spaceFreer.init_();
    }

    @Override
    public InputStream getBlock(final ContentBlockHash key) throws IOException
    {
        File file = acquireBlock(key);
        boolean ok = false;
        try {
            InputStream in = new FileInputStream(file);
            try {
                in = new FilterInputStream(in) {
                    private boolean _released = false;
                    @Override
                    public void close() throws IOException
                    {
                        try {
                            super.close();
                        } finally {
                            if (!_released) {
                                _released = true;
                                releaseBlock(key);
                            }
                        }
                    }
                };
                ok = true;
                return in;
            } finally {
                if (!ok) in.close();
            }
        } finally {
            if (!ok) releaseBlock(key);
        }
    }

    @Override
    public EncoderWrapping wrapForEncoding(OutputStream out) throws IOException
    {
        return _bsb.wrapForEncoding(out);
    }

    @Override
    public void putBlock(ContentBlockHash key, InputStream input, long decodedLength, Object encoderData)
            throws IOException
    {
        _bsb.putBlock(key, input, decodedLength, encoderData);
    }

    @Override
    public void deleteBlock(ContentBlockHash key, TokenWrapper tk) throws IOException
    {
        try {
            deleteFromCache_(key);
        } catch (SQLException e) {
            // TODO: fully reset cache on failure instead of aborting deletion?
            throw new IOException("Failed to remove block from cache " + key);
        }
        _bsb.deleteBlock(key, tk);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static File[] listFiles(File dir) throws IOException
    {
        final File[] files = dir.listFiles();
        if (files == null) return new File[0];
        return files;
    }

    private static final String SUFFIX = ".chunk";

    private File getFileForKey(byte[] key) throws IOException
    {
        String hex = BaseUtil.hexEncode(key);
        String prefix = hex.substring(0, 2);
        File dir = new File(_cacheDir, prefix);
        String name = hex + SUFFIX;
        return new File(dir, name);
    }

    private byte[] getKeyFromFile(File file)
    {
        String name = file.getName();
        int hexSize = ContentBlockHash.UNIT_LENGTH * 2;
        if (name.length() != hexSize + SUFFIX.length()) return null;
        if (!name.endsWith(SUFFIX)) return null;
        byte[] bytes;
        try {
            bytes = BaseUtil.hexDecode(name, 0, hexSize);
        } catch (ExFormatError e) {
            return null;
        }
        return bytes;
    }

    /**
     * Acquire a block, downloading and caching if necessary.
     *
     * The block should be released when it is no longer needed.
     *
     * This may be called from any thread.
     *
     * @param key block key
     * @return file containing chunk data
     */
    File acquireBlock(final ContentBlockHash key) throws IOException
    {
        // TODO: pipeline chunk download
        Date date = new Date();
        final File file = getFileForKey(key.getBytes());

        CacheStatus status;
        FutureTask<File> future = null;

        synchronized (_statusMap) {
            status = _statusMap.get(key);
            if (status == null) {
                status = new CacheStatus();
                _statusMap.put(key, status);
                future = new FutureTask<File>(new Callable<File>() {
                    @Override
                    public File call() throws Exception
                    {
                        downloadBlock(key, file);
                        return file;
                    }
                });
                status._future = future;
            }
            ++status._count;
        }

        boolean ok = false;
        try {
            if (future != null) future.run();
            File f = status._future.get();
            access(key, date);
            ok = true;
            return f;
        } catch (InterruptedException e) {
            // unwrap IOExceptions to avoid multi-level wrapping of exceptions thrown by the
            // proxied backend
            if (e.getCause() != null && e.getCause() instanceof IOException) {
                throw (IOException)e.getCause();
            }
            throw new IOException(e);
        } catch (ExecutionException e) {
            // unwrap IOExceptions to avoid multi-level wrapping of exceptions thrown by the
            // proxied backend
            if (e.getCause() != null && e.getCause() instanceof IOException) {
                throw (IOException)e.getCause();
            }
            throw new IOException(e);
        } catch (SQLException e) {
            throw new IOException(e);
        } finally {
            if (!ok) releaseBlock(key);
        }
    }

    /**
     * Release an acquired chunk.
     *
     * This may be called from any thread.
     *
     * @param key block key
     */
    void releaseBlock(ContentBlockHash key)
    {
        synchronized (_statusMap) {
            CacheStatus status = _statusMap.get(key);
            --status._count;
            if (status._count == 0) {
                _statusMap.remove(key);
            }
        }
    }

    private void downloadBlock(ContentBlockHash key, File file) throws IOException
    {
        if (file.exists()) return;
        FileUtil.ensureDirExists(file.getParentFile());

        /**
         * We use a temporary file for the download to make sure we don't end up with invalid blocks
         * in the cache folder in case of a crash.  We still want to keep these in the auxroot
         * to preserve atomicity of renameTo() (and some OSes have issues if you try to rename
         * things from /tmp elsewhere, and SELinux will complain...so just use the auxroot)
         */
        File tempFile = FileUtil.createTempFile("cache", null, _downloadDir);
        OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile));
        try {
            InputStream in = _bsb.getBlock(key);
            try {
                ByteStreams.copy(in, out);
            } finally {
                in.close();
            }
        } finally {
            out.close();
        }

        FileUtil.rename(tempFile, file);
    }

    private void access(ContentBlockHash hash, Date date) throws SQLException
    {
        synchronized (_accessTimeSync) {
            Date oldAccessTime = _accessTimeCache.getIfPresent(hash);
            if (oldAccessTime != null) {
                if (date.getTime() - oldAccessTime.getTime() <= SLACK_MILLIS) return;
            }
            if (_accessTimeDirtyMap.isEmpty()) {
                scheduleAccessTimeWriteback();
            }
            _accessTimeDirtyMap.put(hash, date);
            _accessTimeCache.put(hash, date);
        }
    }

    private void scheduleAccessTimeWriteback()
    {
        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                try {
                    writeDirtyMap_();
                } catch (SQLException e) {
                    l.error(Util.e(e));
                }
                // this has to be run in a core thread
                _spaceFreer.schedule_();
            }
        }, 0);
    }

    /*
     * cache manipulations and locks:
     * - acquireChunk (_statusMap)
     *   - accept (_accessTimeDirtyMap)
     * - releaseChunk (_statusMap)
     * - writeDirtyMap_ (_accessTimeDirtyMap)
     * - freeSomeSpace_ (_statusMap)
     *   - deleteFromCache_ (_statusMap)
     */

    private void writeDirtyMap_() throws SQLException
    {
        final Map<ContentBlockHash, Date> map;
        synchronized (_accessTimeSync) {
            // swap maps to minimize time holding the lock
            map = _accessTimeDirtyMap;
            if (map.isEmpty()) return;
            _accessTimeDirtyMap = _oldAccessTimeDirtyMap;
            _oldAccessTimeDirtyMap = map;
        }

        if (l.isDebugEnabled()) {
            l.debug("writing " + map.size() + " dirty cache access entries");
        }
        Trans t = _tm.begin_();
        try {
            for (Map.Entry<ContentBlockHash, Date> entry : map.entrySet()) {
                ContentBlockHash key = entry.getKey();
                Date accessTime = entry.getValue();
                if (accessTime != null) {
                    _cdb.setCacheAccess(key.getBytes(), accessTime.getTime(), t);
                } else {
                    _cdb.deleteCachedEntry(key.getBytes(), t);
                }
            }
            t.commit_();
        } finally {
            map.clear();
            t.end_();
        }
    }

    class SpaceFreer
    {
        DelayedScheduler _delayedScheduler;

        void init_()
        {
            _delayedScheduler = new DelayedScheduler(_sched, SLEEP_MILLIS, new Runnable() {
                @Override
                public void run()
                {
                    try {
                        freeSomeSpace_();
                    } catch (SQLException e) {
                        l.warn(Util.e(e));
                    }
                }
            });
            schedule_();
        }

        long getTotalSpace()
        {
            return _cacheDir.getTotalSpace();
        }

        long getFreeSpace()
        {
            return _cacheDir.getUsableSpace();
        }

        void schedule_()
        {
            _delayedScheduler.schedule_();
        }

        void freeSomeSpace_() throws SQLException
        {
            long totalSpace = getTotalSpace();
            long freeSpaceLow = (long)(totalSpace * FREE_SPACE_LOW);

            long freeSpace = getFreeSpace();
            l.debug("free space: " + Util.formatSize(freeSpace) +
                    " needed: " + Util.formatSize(freeSpaceLow));
            if (freeSpace > freeSpaceLow) return;

            long freeSpaceHigh = (long)(totalSpace * FREE_SPACE_HIGH);
            long needed = freeSpaceHigh - freeSpace;
            long reclaimed = 0;
            IDBIterator<ContentBlockHash> it = _cdb.getSortedCacheAccessesIter();
            try {
                while (reclaimed < needed) {
                    if (getFreeSpace() >= freeSpaceHigh) break;
                    if (!it.next_()) break;
                    reclaimed += deleteFromCache_(it.get_());
                }
            } finally {
                it.close_();
            }
        }
    }

    private long deleteFromCache_(ContentBlockHash b) throws SQLException
    {
        byte[] key = b.getBytes();
        synchronized (_statusMap) {
            CacheStatus status = _statusMap.get(key);
            if (status != null) {
                if (status._count > 0) return 0;
            }

            try {
                File file = getFileForKey(key);
                long length = file.length();

                // We must hold the lock to the status map here; otherwise, some other
                // thread may try to acquire the block we are deleting
                assert Thread.holdsLock(_statusMap);

                if (l.isDebugEnabled()) l.debug("deleting " + file);
                if (file.exists()) FileUtil.delete(file);

                // FIXME: should this use the dirty access time map???
                Trans t = _tm.begin_();
                try {
                    _cdb.deleteCachedEntry(key, t);
                    t.commit_();
                } finally {
                    t.end_();
                }

                return length;
            } catch (IOException e) {
                l.warn(Util.e(e));
                return 0;
            }
        }
    }
}
