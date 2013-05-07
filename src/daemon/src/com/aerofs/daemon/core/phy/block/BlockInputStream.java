/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.lib.ContentHash;
import com.aerofs.lib.LibParam;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.InputStream;

import static com.aerofs.daemon.core.phy.block.BlockUtil.*;

public class BlockInputStream extends InputStream
{
    private final IBlockStorageBackend _bsb;
    private final ContentHash _hash;
    private final int _numChunks;

    private int _chunkIndex;
    private long _pos;

    private InputStream _in;

    public BlockInputStream(IBlockStorageBackend bsb, ContentHash hash)
    {
        _bsb = bsb;
        _hash = hash;
        _numChunks = getNumBlocks(hash);
    }

    @Override
    public int read() throws IOException
    {
        byte[] b = new byte[1];
        if (read(b) < 0) return -1;
        else return b[0] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        if (len == 0) return 0;
        if (_chunkIndex >= _numChunks) return -1;
        if (_in == null) resetInputStream();

        int pos = off;
        final int end = off + len;
        while (pos < end && _chunkIndex < _numChunks) {
            int read = _in.read(b, pos, end - pos);
            if (read < 0) {
                ++_chunkIndex;
                resetInputStream();
                if (_chunkIndex >= _numChunks) break;
            } else {
                pos += read;
                _pos += read;
            }
        }

        int read = pos - off;
        if (read == 0) return -1;
        return read;
    }

    @Override
    public long skip(long n) throws IOException
    {
        Preconditions.checkArgument(n >= 0);
        final long oldPos = _pos;
        long newPos = _pos + n;
        int newChunkIndex = (int)(newPos / LibParam.FILE_BLOCK_SIZE);
        if (newChunkIndex != _chunkIndex) {
            _chunkIndex = newChunkIndex;
            _pos = _chunkIndex * LibParam.FILE_BLOCK_SIZE;
            closeInputStream();
        }
        if (_in == null) resetInputStream();
        if (_pos != newPos) {
            long skipped = _in.skip(newPos - _pos);
            _pos += skipped;
        }
        return _pos - oldPos;
    }

    @Override
    public void close() throws IOException
    {
        _chunkIndex = _numChunks;
        closeInputStream();
    }

    private void closeInputStream() throws IOException
    {
        if (_in != null) {
            _in.close();
            _in = null;
        }
    }

    private void resetInputStream() throws IOException
    {
        closeInputStream();
        if (_chunkIndex < _numChunks) {
            _in = _bsb.getBlock(getBlock(_hash, _chunkIndex));
        }
    }
}