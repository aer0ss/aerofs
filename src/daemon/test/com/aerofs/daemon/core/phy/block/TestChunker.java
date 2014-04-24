/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.core.phy.block.IBlockStorageBackend.EncoderWrapping;
import com.google.common.io.InputSupplier;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class TestChunker extends AbstractBlockTest
{
    @Mock IBlockStorageBackend bsb;

    private static abstract class StreamSupplier implements InputSupplier<InputStream>
    {
        protected final long _length;

        private class Stream extends InputStream
        {
            private long _offset;
            private final StreamSupplier _supplier;

            Stream(StreamSupplier supplier) { _supplier = supplier; }

            @Override
            public int read() throws IOException
            {
                return _supplier.read(_offset++);
            }

            @Override
            public long skip(long n) throws IOException
            {
                _offset += n;
                return n;
            }
        }

        StreamSupplier(long size) { _length = size; }

        @Override
        public InputStream getInput() throws IOException
        {
            return new Stream(this);
        }

        protected abstract int read(long offset) throws IOException;
    }

    private static class Chunker extends AbstractChunker
    {
        public Chunker(StreamSupplier supplier, IBlockStorageBackend bsb)
        {
            super(supplier, supplier._length, bsb);
        }

        @Override
        protected StorageState prePutBlock_(Block block) throws SQLException
        {
            return StorageState.NEEDS_STORAGE;
        }

        @Override
        protected void postPutBlock_(Block block) throws SQLException {}
    }


    private static class DevZero extends StreamSupplier
    {
        DevZero(long size) { super(size); }

        @Override
        public int read(long offset) throws IOException
        {
            if (offset >= _length) return -1;
            //if ((offset & 0xffff) == 0) l.info("read " + offset);
            return 0;
        }
    }

    @Before
    public void setUp() throws Exception
    {
        // no encoding is performed by the mock backend
        when(bsb.wrapForEncoding(any(OutputStream.class))).thenAnswer(new Answer<Object>()
        {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                Object[] args = invocation.getArguments();
                return new EncoderWrapping((OutputStream)args[0], null);
            }
        });
    }

    // TODO: use env var or property or something to selectively enable slow tests on CI
    @Test
    @Ignore
    public void shouldSupportInputLargerThan2GB() throws Exception
    {
        new Chunker(new DevZero((1L << 31) + 1), bsb).splitAndStore_();
    }
}
