/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.base.C;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend.EncoderWrapping;
import com.aerofs.daemon.lib.HashStream;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LengthTrackingOutputStream;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.ResettableFileInputStream;
import com.google.common.base.Preconditions;
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
        Preconditions.checkArgument(length >= 0);
        _input = input;
        _length = length;
        _bsb = bsb;
    }

    public void setSkipEmpty(boolean skip)
    {
        _skipEmpty = skip;
    }

    public ContentBlockHash splitAndStore_() throws IOException, SQLException
    {
        if (_length == 0 && _skipEmpty) return new ContentBlockHash(new byte[0]);

        // To avoid integer overflows, make sure the hashBytes buffer can be 32bit-indexed
        // With a block size of 4MB and a hash size of 16bytes that means a file size cutoff of
        // 500TB so we should be safe...
        Preconditions.checkArgument(_length <
                (long)Integer.MAX_VALUE * LibParam.FILE_BLOCK_SIZE * ContentBlockHash.UNIT_LENGTH);

        int trailing = (int)(_length % LibParam.FILE_BLOCK_SIZE);
        int numBlocks = (int)(_length / LibParam.FILE_BLOCK_SIZE) +
                (trailing > 0 || _length == 0 ? 1 : 0);

        byte[] hashBytes = new byte[numBlocks * ContentBlockHash.UNIT_LENGTH];

        for (int i = 0; i < numBlocks; ++i) {
            int blockSize = i == numBlocks - 1 ? trailing : LibParam.FILE_BLOCK_SIZE;
            ContentBlockHash h = storeOneBlock_(i, blockSize);
            System.arraycopy(h.getBytes(), 0, hashBytes, i * ContentBlockHash.UNIT_LENGTH,
                    ContentBlockHash.UNIT_LENGTH);
        }

        return new ContentBlockHash(hashBytes);
    }

    public class Block
    {
        long _length;
        ContentBlockHash _hash;
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
        public void close() throws IOException
        {
            d.reset();
        }
    }

    private static class FileEncodingBuffer implements IEncodingBuffer
    {
        private final File f;

        FileEncodingBuffer() throws IOException
        {
            f = FileUtil.createTempFile("encodebuffer", null, null);
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

    public enum StorageState
    {
        ALREADY_STORED,
        NEEDS_STORAGE
    }
    // Returns whether or not this call needs additional action in the backend.
    // i.e. if a block is already stored, return ALREADY_STORED
    //      if this is the first time we've seen this block, return NEEDS_STORAGE
    protected abstract StorageState prePutBlock_(Block block) throws SQLException;
    protected abstract void postPutBlock_(Block block) throws SQLException;

    private ContentBlockHash storeOneBlock_(long index, int blockSize) throws IOException, SQLException
    {
        InputSupplier<? extends InputStream> input
                = ByteStreams.slice(_input, index * LibParam.FILE_BLOCK_SIZE, LibParam.FILE_BLOCK_SIZE);

        // read a chunk of input into a buffer, perform any backend-specific encoding and
        // compute any metadata (hash, length, ...) required for the actual write
        Block block = new Block();
        IEncodingBuffer buffer = makeEncodingBuffer(blockSize);
        try {
            OutputStream out = wrapOutputStream(block, buffer.encoderOutput());
            try {
                ByteStreams.copy(input, out);
            } finally {
                out.close();
            }
            // write chunk into persistent storage, keyed by content hash
            StorageState blockState = prePutBlock_(block);
            if (blockState == StorageState.NEEDS_STORAGE) {
                InputStream encoded = buffer.encoded();
                try {
                    _bsb.putBlock(block._hash, encoded, block._length, block._encoderData);
                } finally {
                    encoded.close();
                }
            }
            postPutBlock_(block);
        } finally {
            buffer.close();
        }
        return block._hash;
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
                    ContentBlockHash hash = hs.getHashAttrib();
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
