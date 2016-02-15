package com.aerofs.daemon.lib;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.lib.*;
import com.google.common.io.ByteStreams;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.aerofs.testlib.AbstractTest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class TestHashStream extends AbstractTest
{
    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    private Random _random = new Random(0);

    private ContentBlockHash _outHashAttrib;
    private ContentBlockHash _inHashAttrib;

    public static interface RandomWriter
    {
        public void writeTestData(OutputStream out) throws IOException;
    }

    @Test
    public void shouldProduceNonemptyHashForEmptyFile() throws Exception
    {
        runTest(out -> {
        });
        Assert.assertTrue(_outHashAttrib.getBytes().length > 0);
    }

    @Test
    public void shouldMatchOldHashImpl() throws Exception
    {
        runTest(out -> {
            for (int j = 0; j < 40; ++j) {
                int size = _random.nextInt(1024);
                writeRandomBytes(out, size);
            }
        });
    }

    @Test
    public void shouldChunkProperly() throws Exception
    {
        runTest(out -> {
            int chunkSize = (int) ClientParam.FILE_BLOCK_SIZE;
            writeRandomBytes(out, chunkSize);
            writeRandomBytes(out, chunkSize / 2);
            writeRandomBytes(out, chunkSize);
            writeRandomBytes(out, chunkSize * 2);
            writeRandomBytes(out, chunkSize / 2);
            writeRandomBytes(out, chunkSize * 3 / 2);
            writeRandomBytes(out, chunkSize * 3 / 2);
        });
    }

    private void writeRandomBytes(OutputStream out, int size) throws IOException {
        byte[] bytes = new byte[size];
        _random.nextBytes(bytes);
        out.write(bytes);
        l.trace("wrote {} bytes", size);
    }

    private void runTest(RandomWriter rw) throws Exception {
        HashStream outHashStream = HashStream.newFileHasher();
        File tempFile = FileUtil.createTempFile("testHashStream.", ".tmp", _tempFolder.getRoot());
        try (OutputStream out = outHashStream.wrap(new FileOutputStream(tempFile))) {
            rw.writeTestData(out);
        }
        assertEquals(tempFile.length(), outHashStream.getLength());
        _outHashAttrib = outHashStream.getHashAttrib();
        l.debug("outHashAttrib: " + _outHashAttrib);

        ContentHash hash;
        HashStream inHashStream = HashStream.newFileHasher();
        try (InputStream in = inHashStream.wrap(new FileInputStream(tempFile))) {
            hash = new ContentHash(BaseSecUtil.hash(ByteStreams.toByteArray(in)));
        }
        _inHashAttrib = inHashStream.getHashAttrib();

        assertEquals(_inHashAttrib, _outHashAttrib);
        assertArrayEquals(_inHashAttrib.getBytes(), _inHashAttrib.getBytes());
        if (_inHashAttrib.getBytes().length == ContentHash.LENGTH) {
            assertArrayEquals(_inHashAttrib.getBytes(), hash.getBytes());
        }

        FileUtil.deleteNow(tempFile);
    }
}
