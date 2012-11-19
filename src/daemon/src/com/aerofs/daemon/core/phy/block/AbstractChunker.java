/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.core.phy.block.IBlockStorageBackend.EncoderWrapping;
import com.aerofs.daemon.lib.HashStream;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.LengthTrackingOutputStream;
import com.aerofs.lib.Param;
import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;

import static com.aerofs.daemon.core.phy.block.BlockUtil.BLOCK_HASH_SIZE;
import static com.aerofs.daemon.core.phy.block.BlockUtil.FILE_BLOCK_SIZE;

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

        int numBlocks = _length == 0 ? 1 : (int)((_length + FILE_BLOCK_SIZE - 1) / FILE_BLOCK_SIZE);
        byte[] hashBytes = new byte[numBlocks * BLOCK_HASH_SIZE];

        for (int i = 0; i < numBlocks; ++i) {
            ContentHash h = storeOneBlock_(i);
            System.arraycopy(h.getBytes(), 0, hashBytes, i * BLOCK_HASH_SIZE, BLOCK_HASH_SIZE);
        }

        return new ContentHash(hashBytes);
    }

    public class Block
    {
        long _length;
        ContentHash _hash;
        Object _encoderData;
    }

    protected abstract void prePutBlock_(Block block) throws SQLException;
    protected abstract void postPutBlock_(Block block) throws SQLException;

    private ContentHash storeOneBlock_(int index) throws IOException, SQLException
    {
        InputSupplier<? extends InputStream> input
                = ByteStreams.slice(_input, index * FILE_BLOCK_SIZE, FILE_BLOCK_SIZE);

        InputStream in = input.getInput();
        try {
            // read a chunk of input into an in-memory buffer, performs any backend-specific
            // encoding and metadata computation (hash, length, ...) required for actual write
            Block block = new Block();
            ByteArrayOutputStream d = new ByteArrayOutputStream((int)FILE_BLOCK_SIZE);
            OutputStream out = wrapOutputStream(block, d);
            try {
                byte[] buffer = new byte[Param.FILE_BUF_SIZE];
                for (int n; (n = in.read(buffer)) > 0;) {
                    out.write(buffer, 0, n);
                }
            } finally {
                out.close();
            }
            // write chunk into persistent storage, keyed by content hash
            // NOTE: ByteArrayInputStream supports reset() as expected by putBlock()
            prePutBlock_(block);
            _bsb.putBlock(block._hash, new ByteArrayInputStream(d.toByteArray()), block._length,
                    block._encoderData);
            postPutBlock_(block);
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
