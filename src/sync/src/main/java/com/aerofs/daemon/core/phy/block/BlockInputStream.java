/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.lib.ClientParam;
import com.aerofs.lib.ContentBlockHash;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.InputStream;

import static com.aerofs.daemon.core.phy.block.BlockUtil.*;

public class BlockInputStream extends InputStream
{
    private final IBlockStorageBackend _bsb;
    private final ContentBlockHash _hash;
    private final int _numChunks;
    private final long _length;

    private int _chunkIndex;
    private long _pos;

    private InputStream _in;

    public BlockInputStream(IBlockStorageBackend bsb, ContentBlockHash hash, long length)
    {
        _bsb = bsb;
        _hash = hash;
        _length = length;
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
                // all non-last blocks are expected to be full-sized
                if (_pos != ClientParam.FILE_BLOCK_SIZE * _chunkIndex) {
                    throw new IOException("invalid block file: "
                            + _chunkIndex + " " + _pos + "/" + _length);
                }
            } else {
                pos += read;
                _pos += read;
            }
        }

        int read = pos - off;
        if (read == 0) {
            if (_pos < _length) throw new IOException("unexpected EOF: " + _pos + "/" + _length);
            return -1;
        }
        return read;
    }

    @Override
    public long skip(long n) throws IOException
    {
        Preconditions.checkArgument(n >= 0);
        final long oldPos = _pos;
        long newPos = _pos + n;
        int newChunkIndex = (int)(newPos / ClientParam.FILE_BLOCK_SIZE);
        if (newChunkIndex != _chunkIndex) {
            _chunkIndex = newChunkIndex;
            _pos = Math.min((long)_chunkIndex * ClientParam.FILE_BLOCK_SIZE, _length);
            closeInputStream();
        }
        if (_in == null) resetInputStream();
        if (_in != null && _pos != newPos) {
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