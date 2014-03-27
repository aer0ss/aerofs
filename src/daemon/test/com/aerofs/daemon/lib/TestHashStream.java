package com.aerofs.daemon.lib;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.ContentHash;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.aerofs.daemon.core.Hasher;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LibParam;
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
        runTest(new RandomWriter() {
            @Override
            public void writeTestData(OutputStream out) throws IOException
            {
            }
        });
        Assert.assertTrue(_outHashAttrib.getBytes().length > 0);
    }

    @Test
    public void shouldMatchOldHashImpl() throws Exception
    {
        runTest(new RandomWriter() {
            @Override
            public void writeTestData(OutputStream out) throws IOException
            {
                for (int j = 0; j < 40; ++j) {
                    int size = _random.nextInt(1024);
                    writeRandomBytes(out, size);
                }
            }
        });
    }

    @Test
    public void shouldChunkProperly() throws Exception
    {
        runTest(new RandomWriter() {
            @Override
            public void writeTestData(OutputStream out) throws IOException
            {
                int chunkSize = LibParam.FILE_BLOCK_SIZE;
                writeRandomBytes(out, chunkSize);
                writeRandomBytes(out, chunkSize / 2);
                writeRandomBytes(out, chunkSize);
                writeRandomBytes(out, chunkSize * 2);
                writeRandomBytes(out, chunkSize / 2);
                writeRandomBytes(out, chunkSize * 3 / 2);
                writeRandomBytes(out, chunkSize * 3 / 2);
            }
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
        OutputStream out = outHashStream.wrap(new BufferedOutputStream(new FileOutputStream(tempFile)));
        try {
            rw.writeTestData(out);
        } finally {
            out.close();
        }
        assertEquals(tempFile.length(), outHashStream.getLength());
        _outHashAttrib = outHashStream.getHashAttrib();
        l.debug("outHashAttrib: " + _outHashAttrib);

        ContentHash hash;
        HashStream inHashStream = HashStream.newFileHasher();
        InputStream in = inHashStream.wrap(new FileInputStream(tempFile));
        try {
            hash = Hasher.computeHashImpl(in, tempFile.length(), null);
        } finally {
            in.close();
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
