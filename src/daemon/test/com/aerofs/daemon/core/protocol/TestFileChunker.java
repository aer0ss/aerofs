/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.C;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.lib.fs.FileChunker;
import com.aerofs.testlib.AbstractTest;
import org.junit.Test;
import org.junit.internal.ArrayComparisonFailure;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.ceil;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.when;

public class TestFileChunker extends AbstractTest
{
    private static class TestParams
    {
        final int fileLength, prefixLength, chunkSize;
        final String description;

        TestParams(int fileLength, int prefixLength, int chunkSize, String description)
        {
            this.fileLength = fileLength;
            this.prefixLength = prefixLength;
            this.chunkSize = chunkSize;
            this.description = description;
        }
    }

    @Test
    public void shouldReadChunksCorrectly()
            throws IOException, ExUpdateInProgress
    {
        TestParams[] params = {
                // Small numbers
                new TestParams(1, 0, 1, "one byte file, one byte chunks"),
                new TestParams(1, 0, 10, "one byte file, larger chunks"),
                new TestParams(97, 5, 7, "Prime numbers"),

                // Larger numbers, more than 128 chunks
                new TestParams(1024, 0, 2, "512 2-bytes chunks"),
                new TestParams(2*C.MB, 0, 8*C.KB, "2 MB file, 8 KB chunks, no prefix"),
                new TestParams(2*C.MB, 7, 8*C.KB, "2 MB file, 8 KB chunks, 7 bytes prefix"),
                new TestParams(2*C.MB + 1, 7, 8*C.KB, "2 MB + 1 file, 8 KB chunks, 7 bytes prefix"),

                // Corner cases
                new TestParams(0, 0, 10, "empty file"),
                new TestParams(0, 0, 0, "empty file, 0-sized chunks"),
                new TestParams(1, 1, 10, "prefix reaches EOF"),
        };

        for (TestParams p : params) {
            l.info("Testing: {}", p.description);
            doTest(p.fileLength, p.prefixLength, p.chunkSize);
        }
    }

    @Test(expected = ExUpdateInProgress.class)
    public void shouldFailIfPrefixLargerThanFile() throws IOException, ExUpdateInProgress
    {
        doTest(100, 101, 10);
    }

    @Test(expected = ExUpdateInProgress.class)
    public void shouldFailIfFileShorterThanExpected() throws IOException, ExUpdateInProgress
    {
        ChunkChecker checker = new ChunkChecker(200, 0); // 200 bytes file
        FileChunker chunker = new FileChunker(checker.getFile(), 0xdead, 201, 0, 1, true); // try to read 201 bytes

        byte[] chunk;
        while ((chunk = chunker.getNextChunk_()) != null) {
            checker.checkChunk(chunk);
        }
    }

    @Test
    public void shouldFailIfMtimechanged() throws IOException, ExUpdateInProgress
    {
        ChunkChecker checker = new ChunkChecker(200, 0); // 200 bytes file
        FileChunker chunker = new FileChunker(checker.getFile(), 0xdead, 200, 0, 1, false); // try to read 200 bytes

        byte[] chunk = chunker.getNextChunk_();
        checker.checkChunk(chunk);
        when(checker.file.wasModifiedSince(anyLong(), anyLong())).thenReturn(true);
        try {
            chunker.getNextChunk_();
            fail();
        } catch (ExUpdateInProgress e) {}
    }

    @Test
    public void shouldFailIfChunksDontMatch() throws IOException, ExUpdateInProgress
    {
        ChunkChecker checker = new ChunkChecker(200, 0); // 0-byte prefix
        FileChunker chunker = new FileChunker(checker.getFile(), 0xdead, 200, 1, 10, true); // 1 byte prefix

        // Therefore, since the chunker is reading at a different offset than the checker, the test
        // should fail.

        byte[] chunk = chunker.getNextChunk_();
        try {
            checker.checkChunk(chunk);
            fail();
        } catch (ArrayComparisonFailure e) {}
    }

    //
    // Helper methods and classes
    //

    private void doTest(int fileLength, int prefixLength, int chunkSize)
            throws IOException, ExUpdateInProgress
    {
        ChunkChecker checker = new ChunkChecker(fileLength, prefixLength);
        FileChunker chunker = new FileChunker(checker.getFile(), 0xdead,
                Math.max(fileLength, prefixLength), // expected file size MUST be >= prefix
                prefixLength, chunkSize, true);

        byte[] chunk;
        while ((chunk = chunker.getNextChunk_()) != null) {
            checker.checkChunk(chunk);
        }

        chunker.close_();
        checker.checkNoMoreChunks();

        assertEquals(getExpectedClosedCount(fileLength, prefixLength, chunkSize), checker.getClosedCount());
    }

    /**
     * Calculates how many times we expect to see close() called on the input stream on Windows
     */
    private int getExpectedClosedCount(int fileSize, int prefix, int chunkSize)
    {
        int chunkCount = (int) ceil((float)(fileSize - prefix) / chunkSize);
        return (int) ceil((float)chunkCount / FileChunker.QUEUE_SIZE_WINDOWS) + 1; // +1 for the last close
    }

    /**
     * Helper class to create a mock IPhysicalFile and check that we've read the correct sequence of
     * chunks from it.
     */
    private static class ChunkChecker
    {
        private static final int MAX_FILE_SIZE = 5 * C.MB;
        private static final byte[] RANDOM_BYTES = new byte[MAX_FILE_SIZE];
        static {
            Random r = new Random(0); // use a fixed seed to get the same numbers between runs
            r.nextBytes(RANDOM_BYTES);
        }

        @Mock IPhysicalFile file;
        private final CountingBAIS testStream;
        private final InputStream controlStream;

        ChunkChecker(int fileLength, int prefix)
                throws IOException
        {
            MockitoAnnotations.initMocks(this);
            checkState(fileLength <= MAX_FILE_SIZE);

            testStream = new CountingBAIS(RANDOM_BYTES, 0, fileLength);
            controlStream = new ByteArrayInputStream(RANDOM_BYTES, 0, fileLength);

            controlStream.skip(prefix);
        }

        IPhysicalFile getFile()
                throws IOException
        {
            when(file.newInputStream()).thenReturn(testStream);
            return file;
        }

        void checkChunk(byte[] chunk)
                throws IOException
        {
            // Read a chunk of same length from the control input stream
            byte[] expected = new byte[chunk.length];
            int bytesRead = controlStream.read(expected);
            assertEquals(expected.length, bytesRead);

            assertArrayEquals(expected, chunk);
        }

        void checkNoMoreChunks()
                throws IOException
        {
            // Check that we've read all the data from the control input stream
            assertEquals(0, controlStream.available());
        }

        int getClosedCount()
        {
            return testStream.closedCount;
        }

        private static class CountingBAIS extends ByteArrayInputStream
        {
            int closedCount;

            CountingBAIS(byte[] bytes, int offset, int length)
            {
                super(bytes, offset, length);
            }

            @Override
            public void close() throws IOException
            {
                super.close();
                closedCount++;

                // IMPORTANT: On byte array input streams, close() does nothing. But FileChunker
                // expects that closing and re-opening the stream will reset the read position to 0
                // Therefore, we need to do it manually here.
                pos = 0;
            }
        }
    }
}
