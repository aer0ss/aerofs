package com.aerofs.lib.aws.s3.chunks;

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
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import com.google.inject.Inject;
import org.apache.log4j.Logger;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.DelayedScheduler;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.C;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.aws.s3.chunks.S3ChunkAccessor.AbstractChunkedInputStream;
import com.aerofs.lib.aws.s3.db.S3CacheDatabase;
import com.aerofs.lib.aws.s3.db.S3Database;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.s3.S3Config.S3DirConfig;

public class S3Cache
{
    private static final Logger l = Util.l(S3Cache.class);

    private static int MAX_CACHE_SIZE = 1 * C.KB;

    private static final long SLACK_MILLIS = 1 * C.MIN;
    private static final long SLEEP_MILLIS = 5 * C.MIN;

    private static final double FREE_SPACE_LOW = 0.32;
    private static final double FREE_SPACE_HIGH = 0.36;

    private final TransManager _tm;
    private final CoreScheduler _coreScheduler;
    private final S3CacheDatabase _s3CacheDB;
    private final S3ChunkAccessor _accessor;

    private final File _cacheDir;
    private final File _tempDir;

    private final Map<ContentHash, CacheStatus> _statusMap = Maps.newHashMap();

    private Object _accessTimeSync = new Object();

    private final Cache<ContentHash, Date> _accessTimeCache =
            CacheBuilder.newBuilder().maximumSize(MAX_CACHE_SIZE).build();

    private Map<ContentHash, Date> _accessTimeDirtyMap = Maps.newHashMap();
    private Map<ContentHash, Date> _oldAccessTimeDirtyMap = Maps.newHashMap();

    long _cacheSleepMillis = SLEEP_MILLIS;

    private Executor _coreExecutor;

    SpaceFreer _spaceFreer = new SpaceFreer();

    CacheListener _cacheListener;


    private static class CacheStatus {
        int _count;
        Future<File> _future;

        @Override
        public String toString()
        {
            return String.valueOf(_count);
        }
    }

    static class CacheListener
    {
        void addingToCache(ContentHash chunk)
        {
        }

        void addedToCache(ContentHash hash, File file)
        {
        }

        void deletingFromCache(ContentHash chunk, File file)
        {
        }

        void deletedFromCache(ContentHash chunk)
        {
        }
    }

    class SpaceFreer
    {
        DelayedScheduler _delayedScheduler;

        void init_()
        {
            _delayedScheduler = new DelayedScheduler(_coreScheduler, _cacheSleepMillis, new Runnable() {
                @Override
                public void run()
                {
                    freeSomeSpace_();
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

        void freeSomeSpace_()
        {
            checkHoldingCoreLock();

            try {
                long totalSpace = getTotalSpace();
                long freeSpaceLow = (long)(totalSpace * FREE_SPACE_LOW);

                long freeSpace = getFreeSpace();
                    l.debug("free space: " + Util.formatSize(freeSpace) +
                            " needed: " + Util.formatSize(freeSpaceLow));
                if (freeSpace > freeSpaceLow) return;

                long freeSpaceHigh = (long)(totalSpace * FREE_SPACE_HIGH);
                long needed = freeSpaceHigh - freeSpace;
                long reclaimed = 0;
                IDBIterator<ContentHash> it = _s3CacheDB.getSortedCacheAccessesIter();
                try {
                    while (reclaimed < needed) {
                        if (getFreeSpace() >= freeSpaceHigh) break;
                        if (!it.next_()) break;
                        ContentHash chunk = it.get_();
//                            l.debug("chunk " + chunk);

                        synchronized (_statusMap) {
                            CacheStatus status = _statusMap.get(chunk);
                            if (status != null) {
                                if (status._count > 0) continue;
                            }

                            try {
                                File file = getChunkFile(chunk);
                                long length = file.length();

                                // We must hold the lock to the status map here; otherwise, some other thread may try
                                // to acquire the chunk we are deleting
                                assert Thread.holdsLock(_statusMap);
                                checkHoldingCoreLock();

                                if (_cacheListener != null) _cacheListener.deletingFromCache(chunk, file);
                                if (l.isDebugEnabled()) l.debug("deleting " + file);
                                if (file.exists()) FileUtil.delete(file);

                                // FIXME: should this use the dirty access time map???
                                Trans t = _tm.begin_();
                                try {
                                    _s3CacheDB.deleteCachedEntry(chunk, t);
                                    t.commit_();
                                } finally {
                                    t.end_();
                                }
                                if (_cacheListener != null) _cacheListener.deletedFromCache(chunk);

                                reclaimed += length;
                            } catch (IOException e) {
                                l.warn(Util.e(e));
                            }
                        }
                    }
                } finally {
                    it.close_();
                }
            } catch (SQLException e) {
                l.warn(Util.e(e));
            }
        }
    }


    @Inject
    public S3Cache(
//            PhysicalFile.Factory fileFactory,
            TransManager tm,
            CoreQueue coreQueue,
            CoreScheduler coreScheduler,
            S3Database s3db,
            S3CacheDatabase s3CacheDB,
            S3ChunkAccessor accessor,
            S3DirConfig s3DirConfig)
    {
        _tm = tm;
        _coreScheduler = coreScheduler;
        _s3CacheDB = s3CacheDB;
        _accessor = accessor;
        File s3Dir = s3DirConfig.getS3Dir();
        _cacheDir = new File(s3Dir, C.S3_CACHE_DIR);
        _tempDir = new File(_cacheDir, "tmp");

        _coreExecutor = new Executor() {
            @Override
            public void execute(final Runnable command)
            {
                _coreScheduler.schedule(new AbstractEBSelfHandling() {
                    @Override
                    public void handle_()
                    {
                        command.run();
                    }
                }, 0);
            }
        };
    }

    public void init_() throws IOException
    {
        FileUtil.ensureDirExists(_cacheDir);
        if (_tempDir.exists()) FileUtil.deleteRecursively(_tempDir);
        FileUtil.ensureDirExists(_tempDir);

        _accessor.init_();

        try {
            // scan over all the files in the cache directory, deleting anything we don't know
            // about in the cache database
            for (File child : listFiles(_cacheDir)) {
                if (!child.isDirectory()) continue;
                if (_tempDir.equals(child)) continue;
                for (File file : listFiles(child)) {
                    if (!file.isFile()) {
                        file.delete();
                        continue;
                    }
                    ContentHash chunk = getChunkFromFile(file);
                    if (chunk == null) {
                        file.delete();
                        continue;
                    }
                    if (l.isDebugEnabled()) l.debug("found chunk " + chunk);
                    long accessTime = _s3CacheDB.getCacheAccess(chunk);
                    if (accessTime <= S3Database.DELETED_FILE_DATE) {
                        file.delete();
                    }
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

        _spaceFreer.init_();
    }

    private static File[] listFiles(File dir) throws IOException
    {
        final File[] files = dir.listFiles();
        if (files == null) return new File[0];
        return files;
    }

    public InputStream readChunks(ContentHash hash)
    {
        return new AbstractChunkedInputStream(hash) {
            protected InputStream readChunk(int index, ContentHash chunk) throws IOException
            {
                return readOneChunk(chunk);
            }
        };
    }

    private InputStream readOneChunk(final ContentHash chunk) throws IOException
    {
        File file = acquireChunk(chunk);
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
                                releaseChunk(chunk);
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
            if (!ok) releaseChunk(chunk);
        }
    }

    private Date currentDate()
    {
        return new Date();
    }


    /*
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

    /**
     * Acquire a chunk, downloading and caching if necessary.
     *
     * The chunk should be released when it is no longer needed.
     *
     * This may be called from any thread.
     *
     * @param chunk hash of chunk
     * @return file containing chunk data
     */
    File acquireChunk(final ContentHash chunk) throws IOException
    {
        // TODO: pipeline chunk download
        Date date = currentDate();
        final File file = getChunkFile(chunk);

        CacheStatus status;
        FutureTask<File> future = null;

        synchronized (_statusMap) {
            status = _statusMap.get(chunk);
            if (status == null) {
                status = new CacheStatus();
                _statusMap.put(chunk, status);
                future = new FutureTask<File>(new Callable<File>() {
                    @Override
                    public File call() throws Exception
                    {
                        downloadChunk(chunk, file);
                        if (_cacheListener != null) _cacheListener.addedToCache(chunk, file);
                        return file;
                    }
                });
                status._future = future;
                if (_cacheListener != null) _cacheListener.addingToCache(chunk);
            }
            ++status._count;
        }

        boolean ok = false;
        try {
            if (future != null) future.run();
            File f = status._future.get();
            access(chunk, date);
            ok = true;
            return f;
        } catch (InterruptedException e) {
            throw new IOException(e);
        } catch (ExecutionException e) {
            throw new IOException(e);
        } catch (SQLException e) {
            throw new IOException(e);
        } finally {
            if (!ok) releaseChunk(chunk);
        }
    }

    /**
     * Release an acquired chunk.
     *
     * This may be called from any thread.
     *
     * @param chunk hash of chunk
     */
    void releaseChunk(ContentHash chunk)
    {
        synchronized (_statusMap) {
            CacheStatus status = _statusMap.get(chunk);
            --status._count;
            if (status._count == 0) {
                _statusMap.remove(chunk);
            }
        }
    }

    private void downloadChunk(ContentHash hash, File file) throws IOException
    {
        if (file.exists()) return;
        FileUtil.ensureDirExists(file.getParentFile());

        File tempFile = File.createTempFile("tmp-", "-" + file.getName() + ".tmp", _tempDir);

        OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile));
        try {
            InputStream in = _accessor.readOneChunk(hash);
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

    private void checkHoldingCoreLock()
    {
        // TODO: actually check this
    }

    private void access(ContentHash hash, Date date) throws SQLException
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
        _coreExecutor.execute(new Runnable() {
            @Override
            public void run()
            {
                try {
                    writeDirtyMap_();
                } catch (SQLException e) {
                    l.error(Util.e(e));
                }
                // this has to be run in a core thread
                _spaceFreer.schedule_();
            }
        });
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
        checkHoldingCoreLock();

        final Map<ContentHash, Date> map;
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
            for (Map.Entry<ContentHash, Date> entry : map.entrySet()) {
                ContentHash chunk = entry.getKey();
                Date accessTime = entry.getValue();
                if (accessTime != null) {
                    _s3CacheDB.setCacheAccess(chunk, accessTime.getTime(), t);
                } else {
                    _s3CacheDB.deleteCachedEntry(chunk, t);
                }
            }
            t.commit_();
        } finally {
            map.clear();
            t.end_();
        }
    }


    private static final String SUFFIX = ".chunk";

    private File getChunkFile(ContentHash hash) throws IOException
    {
        assert S3ChunkAccessor.isOneChunk(hash);
        String hex = hash.toHex();
        String prefix = hex.substring(0, 2);
        File dir = new File(_cacheDir, prefix);
        String name = hex + SUFFIX;
        File file = new File(dir, name);
        return file;
    }

    private ContentHash getChunkFromFile(File file)
    {
        String name = file.getName();
        int hexSize = ContentHash.UNIT_LENGTH * 2;
        if (name.length() != hexSize + SUFFIX.length()) return null;
        if (!name.endsWith(SUFFIX)) return null;
        byte[] bytes;
        try {
            bytes = Util.hexDecode(name, 0, hexSize);
        } catch (ExFormatError e) {
            return null;
        }
        return new ContentHash(bytes);
    }
}
