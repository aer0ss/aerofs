package com.aerofs.lib.aws.s3.chunks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;

import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Param;

public class TestS3Cache extends AbstractTestS3Cache
{
    @Test
    public void shouldDownloadOneChunk() throws Exception
    {
        String testString = "test";
        File sourceFile = new File(_tempDir, "source");
        writeFile(sourceFile, testString);
        final ContentHash hash = uploadFile(sourceFile);
        l.debug("hash = " + hash);

        StringBuilder sb = new StringBuilder();
        Reader r = new InputStreamReader(_s3Cache.readChunks(hash), "US-ASCII");
        try {
            CharStreams.copy(r, sb);
        } finally {
            r.close();
        }

        Assert.assertEquals(testString, sb.toString());
    }

    @Test
    public void shouldSupportConcurrentCacheAccess() throws Exception
    {
        String testString = "test";
        File sourceFile = new File(_tempDir, "source");
        writeFile(sourceFile, testString);
        final ContentHash hash = uploadFile(sourceFile);
        l.debug("hash = " + hash);

        // TODO: check concurrent cache access
    }

    @Test
    public void shouldConcatenateChunks() throws Exception
    {
        String testString = "123";
        long length = 1 * Param.FILE_BLOCK_SIZE + (4 << 10);

        File sourceFile = new File(_tempDir, "source");
        writeLargeFile(sourceFile, length, testString.getBytes());
        final ContentHash hash = uploadFile(sourceFile);
        l.debug("hash = " + hash);

        InputSupplier<InputStream> cacheInput = new InputSupplier<InputStream>() {
            @Override
            public InputStream getInput() throws IOException
            {
                return _s3Cache.readChunks(hash);
            }
        };

        Assert.assertTrue(ByteStreams.equal(Files.newInputStreamSupplier(sourceFile), cacheInput));
    }

    @Test
    public void shouldSeekOverChunks() throws Exception
    {
        String testString = "123";
        long length = 1 * Param.FILE_BLOCK_SIZE + (4 << 10);

        File sourceFile = new File(_tempDir, "source");
        writeLargeFile(sourceFile, length, testString.getBytes());
        final ContentHash hash = uploadFile(sourceFile);
        l.debug("hash = " + hash);

        InputSupplier<? extends InputStream> fileInput = Files.newInputStreamSupplier(sourceFile);

        InputSupplier<? extends InputStream> cacheInput = new InputSupplier<InputStream>() {
            @Override
            public InputStream getInput() throws IOException
            {
                return _s3Cache.readChunks(hash);
            }
        };

        checkSlice(fileInput, cacheInput, Param.FILE_BLOCK_SIZE - 10, 20);
        checkSlice(fileInput, cacheInput, Param.FILE_BLOCK_SIZE + 10, 20);
        checkSlice(fileInput, cacheInput, Param.FILE_BLOCK_SIZE, Param.FILE_BLOCK_SIZE);
    }

    void checkSlice(InputSupplier<? extends InputStream> expected,
            InputSupplier<? extends InputStream> actual,
            long offset, long length) throws IOException {
        expected = ByteStreams.slice(expected, offset, length);
        actual = ByteStreams.slice(actual, offset, length);
        Assert.assertTrue(ByteStreams.equal(expected, actual));
    }

}
