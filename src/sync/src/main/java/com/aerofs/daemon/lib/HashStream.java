package com.aerofs.daemon.lib;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.List;

import javax.annotation.Nullable;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.ContentBlockHash;
import com.google.common.collect.Lists;

public class HashStream
{
    private final MessageDigest _md;
    private final int _chunkSize;
    private int _chunkPos;
    private boolean _closed = false;
    private long _length;
    private final List<byte[]> _digests = Lists.newArrayList();

    public static HashStream newFileHasher()
    {
        return new HashStream(BaseSecUtil.newMessageDigest(), (int) ClientParam.FILE_BLOCK_SIZE);
    }

    public HashStream(MessageDigest md, int chunkSize)
    {
        _md = md;
        _chunkSize = chunkSize;
    }

    protected void checkOpen()
    {
        assert !isClosed();
    }

    public boolean isClosed() {
        return _closed;
    }

    public InputStream wrap(InputStream in)
    {
        checkOpen();
        return new HashInputStream(in, this);
    }

    public OutputStream wrap(OutputStream out)
    {
        checkOpen();
        return new HashOutputStream(out, this);
    }

    protected void updateInChunk(final byte[] b, final int off, final int len)
    {
        _md.update(b, off, len);
    }

    protected void endChunk()
    {
        _digests.add(_md.digest());
    }

    public void update(final byte[] b, final int off, final int len)
    {
        checkOpen();
        if (len == 0) return;
        final int end = off + len;
        int pos = off;
        while (_chunkPos + end - pos >= _chunkSize) {
            updateInChunk(b, pos, _chunkSize - _chunkPos);
            endChunk();
            pos += _chunkSize - _chunkPos;
            _chunkPos = 0;
        }
        updateInChunk(b, pos, end - pos);
        _chunkPos += end - pos;
        _length += len;
    }

    public void close()
    {
        if (!_closed) {
            _closed = true;
            if (_chunkPos != 0 || _length == 0) {
                _digests.add(_md.digest());
            }
        }
    }

    public long getLength()
    {
        return _length;
    }

    public List<byte[]> getHashes()
    {
        return _digests;
    }

    public ContentBlockHash getHashAttrib() {
        assert isClosed();
        int len = 0;
        for (byte[] digest : _digests) len += digest.length;
        byte[] bytes = new byte[len];
        int off = 0;
        for (byte[] digest : _digests) {
            System.arraycopy(digest, 0, bytes, off, digest.length);
            off += digest.length;
        }
        assert off == bytes.length;
        return new ContentBlockHash(bytes);
    }

    private static class HashOutputStream extends OutputStream
    {
        private final OutputStream _out;
        private final HashStream _hasher;

        public HashOutputStream(@Nullable OutputStream out, HashStream hasher)
        {
            _out = out;
            _hasher = hasher;
        }

        @Override
        public final void write(int b) throws IOException
        {
            write(new byte[] { (byte)b });
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException
        {
            _hasher.update(b, off, len);
            if (_out != null) _out.write(b, off, len);
        }

        @Override
        public void flush() throws IOException
        {
            if (_out != null) _out.flush();
        }

        @Override
        public void close() throws IOException
        {
            _hasher.close();
            if (_out != null) _out.close();
        }
    }

    private static class HashInputStream extends InputStream {
        private final InputStream _in;
        private final HashStream _hasher;

        public HashInputStream(InputStream in, HashStream hasher)
        {
            _in = in;
            _hasher = hasher;
        }

        @Override
        public final int read() throws IOException
        {
            byte[] data = new byte[1];
            int bytes = read(data);
            if (bytes == -1) return -1;
            assert bytes == 1;
            return data[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            int bytes = _in.read(b, off, len);
            if (bytes != -1) _hasher.update(b, off, bytes);
            return bytes;
        }

        @Override
        public int available() throws IOException
        {
            return _in.available();
        }

        @Override
        public void close() throws IOException
        {
            _hasher.close();
            _in.close();
        }
    }
}
