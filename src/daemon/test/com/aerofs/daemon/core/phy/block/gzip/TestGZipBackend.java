/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.gzip;

import com.aerofs.base.BaseUtil;
import com.aerofs.daemon.core.phy.block.AbstractBlockTest;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend;
import com.aerofs.lib.ContentBlockHash;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Spy;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestGZipBackend extends AbstractBlockTest
{
    private static class InMemoryBackend implements IBlockStorageBackend
    {
        private final Map<ContentBlockHash, byte[]> _blocks = Maps.newHashMap();

        @Override
        public void init_() throws IOException
        {}

        @Override
        public InputStream getBlock(ContentBlockHash key)
                throws IOException
        {
            byte[] d = _blocks.get(key);
            if (d == null) throw new FileNotFoundException();
            l.info("get k: " + key + " v:" + BaseUtil.hexEncode(d));
            return new ByteArrayInputStream(d);
        }

        @Override
        public EncoderWrapping wrapForEncoding(OutputStream out)
                throws IOException
        {
            return new EncoderWrapping(out, null);
        }

        @Override
        public void putBlock(ContentBlockHash key, InputStream input, long decodedLength,
                @Nullable Object encoderData)
                throws IOException
        {
            assert !_blocks.containsKey(key);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteStreams.copy(input, output);
            l.info("put k: " + key + " v:" + BaseUtil.hexEncode(output.toByteArray()));
            _blocks.put(key, output.toByteArray());
        }

        @Override
        public void deleteBlock(ContentBlockHash key, TokenWrapper tk) throws IOException
        {
            _blocks.remove(key);
        }
    }

    @Spy IBlockStorageBackend bsb = new InMemoryBackend();
    GZipBackend gzip;

    @Before
    public void setUp() throws Exception
    {
        gzip = new GZipBackend(bsb);
        gzip.init_();
    }

    @Test
    public void shouldStoreBlock() throws Exception
    {
        TestBlock b = newBlock();

        gzip.putBlock(b._key, new ByteArrayInputStream(b._content), b._content.length, null);

        verify(bsb).putBlock(eq(b._key), any(InputStream.class), eq((long)b._content.length),
                isNull());
    }

    @Test
    public void shouldThrowWhenTryingToFetchInvalidBlock() throws Exception
    {
        try {
            gzip.getBlock(contentHash(new byte[0]));
            Assert.assertFalse(true);
        } catch (FileNotFoundException e) {}
    }

    @Test
    public void shouldFetchStoredBlock() throws Exception
    {
        TestBlock b = newBlock();

        put(gzip, b);
        Assert.assertArrayEquals(b._content, ByteStreams.toByteArray(gzip.getBlock(b._key)));

        verify(bsb, times(1)).getBlock(eq(b._key));
    }
}
