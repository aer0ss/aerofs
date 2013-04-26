package com.aerofs.daemon.lib;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.aerofs.daemon.core.Hasher;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Param;
import com.aerofs.testlib.AbstractTest;

public class TestHashStream extends AbstractTest
{
    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    private Random _random = new Random(0);

    private ContentHash _hasherAttrib;
    private ContentHash _outHashAttrib;
    private ContentHash _inHashAttrib;

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

    @Ignore
    @Test
    public void shouldChunkProperly() throws Exception
    {
        runTest(new RandomWriter() {
            @Override
            public void writeTestData(OutputStream out) throws IOException
            {
                int chunkSize = Param.FILE_BLOCK_SIZE;
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
        l.trace("wrote " + size + " bytes");
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
        Assert.assertEquals(tempFile.length(), outHashStream.getLength());
        _outHashAttrib = outHashStream.getHashAttrib();
        l.debug("outHashAttrib: " + _outHashAttrib);

        HashStream inHashStream = HashStream.newFileHasher();
        InputStream in = inHashStream.wrap(new FileInputStream(tempFile));
        try {
            _hasherAttrib = Hasher.computeHashImpl(in, tempFile.length(), null);
        } finally {
            in.close();
        }
        _inHashAttrib = inHashStream.getHashAttrib();
        l.debug("inHashAttrib:  " + _outHashAttrib);

        l.debug("hasherAttrib:  " + _hasherAttrib);

        Assert.assertEquals(_hasherAttrib, _outHashAttrib);
        Assert.assertArrayEquals(_hasherAttrib.getBytes(), _outHashAttrib.getBytes());
        Assert.assertEquals(_hasherAttrib, _inHashAttrib);
        Assert.assertArrayEquals(_hasherAttrib.getBytes(), _inHashAttrib.getBytes());

        FileUtil.deleteNow(tempFile);
    }
}
