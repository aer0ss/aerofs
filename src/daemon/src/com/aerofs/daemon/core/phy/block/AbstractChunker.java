/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.base.C;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend.EncoderWrapping;
import com.aerofs.daemon.lib.HashStream;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LengthTrackingOutputStream;
import com.aerofs.lib.Param;
import com.aerofs.lib.ResettableFileInputStream;
import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;

/**
 * Split an input source into blocks and store them through the backend
 */
public abstract class AbstractChunker
{
    private final InputSupplier<? extends InputStream> _input;
    private final long _length;
    private final IBlockStorageBackend _bsb;

    private boolean _skipEmpty = true;

    public AbstractChunker(InputSupplier<? extends InputStream> input, long length,
            IBlockStorageBackend bsb)
    {
        _input = input;
        _length = length;
        _bsb = bsb;
    }

    public void setSkipEmpty(boolean skip)
    {
        _skipEmpty = skip;
    }

    public ContentHash splitAndStore_() throws IOException, SQLException
    {
        if (_length == 0 && _skipEmpty) return new ContentHash(new byte[0]);

        int numBlocks = _length == 0 ? 1 : (int)((_length + Param.FILE_BLOCK_SIZE - 1) /
                                                         Param.FILE_BLOCK_SIZE);
        byte[] hashBytes = new byte[numBlocks * ContentHash.UNIT_LENGTH];

        for (int i = 0; i < numBlocks; ++i) {
            int blockSize = i == numBlocks - 1
                    ? (int)(numBlocks * Param.FILE_BLOCK_SIZE - _length)
                    : Param.FILE_BLOCK_SIZE;
            ContentHash h = storeOneBlock_(i, blockSize);
            System.arraycopy(h.getBytes(), 0, hashBytes, i * ContentHash.UNIT_LENGTH,
                    ContentHash.UNIT_LENGTH);
        }

        return new ContentHash(hashBytes);
    }

    public class Block
    {
        long _length;
        ContentHash _hash;
        Object _encoderData;
    }

    /**
     * Due to the use of encoder streams to compute checksums and size of the block contents, we
     * cannot simply stream the data to the backend. For best performance on small blocks we use
     * in-memory buffering, however to avoid OOMs with large blocks we fallback to temporary files
     */
    private static interface IEncodingBuffer extends Closeable
    {
        OutputStream encoderOutput() throws IOException;
        InputStream encoded() throws IOException;
    }

    private static class InMemoryEncodingBuffer implements IEncodingBuffer
    {
        private final ByteArrayOutputStream d = new ByteArrayOutputStream();

        @Override
        public OutputStream encoderOutput() throws IOException
        {
            return d;
        }

        @Override
        public InputStream encoded() throws IOException
        {
            return new ByteArrayInputStream(d.toByteArray());
        }

        @Override
        public void close() throws IOException {}
    }

    private static class FileEncodingBuffer implements IEncodingBuffer
    {
        private final File f;

        FileEncodingBuffer() throws IOException
        {
            f = FileUtil.createTempFile(null, null, null, true);
        }

        @Override
        public OutputStream encoderOutput() throws IOException
        {
            return new FileOutputStream(f);
        }

        @Override
        public InputStream encoded() throws IOException
        {
            return new ResettableFileInputStream(f);
        }

        @Override
        public void close() throws IOException
        {
            FileUtil.deleteOrOnExit(f);
        }
    }

    // use in-memory buffers up to 64kb
    private static final int IN_MEMORY_THRESHOLD = 64 * C.KB;

    private static IEncodingBuffer makeEncodingBuffer(int blockSize) throws IOException
    {
        if (blockSize < IN_MEMORY_THRESHOLD) return new InMemoryEncodingBuffer();
        return new FileEncodingBuffer();
    }

    protected abstract void prePutBlock_(Block block) throws SQLException;
    protected abstract void postPutBlock_(Block block) throws SQLException;

    private ContentHash storeOneBlock_(int index, int blockSize) throws IOException, SQLException
    {
        InputSupplier<? extends InputStream> input
                = ByteStreams.slice(_input, index * Param.FILE_BLOCK_SIZE, Param.FILE_BLOCK_SIZE);

        InputStream in = input.getInput();
        try {
            // read a chunk of input into a buffer, perform any backend-specific encoding and
            // compute any metadata (hash, length, ...) required for the actual write
            Block block = new Block();
            IEncodingBuffer buffer = makeEncodingBuffer(blockSize);
            try {
                OutputStream out = wrapOutputStream(block, buffer.encoderOutput());
                try {
                    ByteStreams.copy(in, out);
                } finally {
                    out.close();
                }
                // write chunk into persistent storage, keyed by content hash
                prePutBlock_(block);
                _bsb.putBlock(block._hash, buffer.encoded(), block._length, block._encoderData);
                postPutBlock_(block);
            } finally {
                buffer.close();
            }
            return block._hash;
        } finally {
            in.close();
        }
    }

    /**
     * Wrap an output stream to compute hash and length of the decoded data and perform any
     * backend-specific encoding before doing the actual backend write
     */
    private OutputStream wrapOutputStream(final Block block, OutputStream out) throws IOException
    {
        boolean ok = false;
        try {
            final HashStream hs = HashStream.newFileHasher();
            EncoderWrapping wrapping = _bsb.wrapForEncoding(out);
            block._encoderData = wrapping.encoderData;
            out = hs.wrap(wrapping.wrapped);
            out = new LengthTrackingOutputStream(out) {
                @Override
                public void close() throws IOException
                {
                    super.close();
                    ContentHash hash = hs.getHashAttrib();
                    if (!BlockUtil.isOneBlock(hash)) {
                        throw new IOException("Too much data for one chunk!");
                    }
                    block._hash = hash;
                    block._length = getLength();
                }
            };
            ok = true;
            return out;
        } finally {
            if (!ok) out.close();
        }
    }
}
