package com.aerofs.lib.aws.s3.chunks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import org.junit.Assert;
import org.junit.Test;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;

import com.aerofs.lib.C;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Param;
import com.aerofs.lib.db.IDBIterator;

public class TestS3Cache_Space extends AbstractTestS3Cache
{
    long _fakeUsedSpace = 0;
    int _cacheSize;

    @Override
    protected void prepareS3Cache(S3Cache s3Cache)
    {
        s3Cache._cacheSleepMillis = 0;
        s3Cache._spaceFreer = s3Cache.new SpaceFreer() {
            @Override
            long getTotalSpace()
            {
                return 10L * C.MB;
            }

            @Override
            long getFreeSpace()
            {
                long space = getTotalSpace() - 4L * C.MB * _cacheSize;
//                long space = getTotalSpace() - _fakeUsedSpace;
//                l.debug("getFreeSpace: " + space);
                return space;
            }

            @Override
            void schedule_()
            {
                l.debug("schedule space freer");
                freeSomeSpace_();
            }
        };
        s3Cache._cacheListener = new S3Cache.CacheListener() {
            @Override
            void addedToCache(ContentHash hash, File file)
            {
                ++_cacheSize;
                _fakeUsedSpace += file.length();
            }
            @Override
            void deletingFromCache(ContentHash chunk, File file)
            {
                --_cacheSize;
                _fakeUsedSpace -= file.length();
            }
        };
    }

    @Test
    public void shouldFreeSpaceInCache() throws Exception
    {
        String testString = "123";
        long length = 3 * Param.FILE_BLOCK_SIZE + (4 << 10);

        File sourceFile = new File(_tempDir, "source");
        writeLargeFile(sourceFile, length, testString.getBytes());
        final ContentHash hash = uploadFile(sourceFile);

        InputSupplier<InputStream> cacheInput = new InputSupplier<InputStream>() {
            @Override
            public InputStream getInput() throws IOException
            {
                return _s3Cache.readChunks(hash);
            }
        };

        Assert.assertTrue(ByteStreams.equal(Files.newInputStreamSupplier(sourceFile), cacheInput));

        _tcTestSetup.drainScheduler_();
        _s3Cache._spaceFreer.freeSomeSpace_();
        Assert.assertEquals(1, _cacheSize);
        Assert.assertEquals(1, countCacheEntries());
    }

    int countCacheEntries() throws SQLException
    {
        int numCacheEntries = 0;
        IDBIterator<ContentHash> it = _s3CacheDB.getSortedCacheAccessesIter();
        try {
            while (it.next_()) {
                ++numCacheEntries;
            }
        } finally {
            it.close_();
        }
        l.debug("cache entries: " + numCacheEntries);
        return numCacheEntries;
    }
}
