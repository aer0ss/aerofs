package com.aerofs.lib;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public class GZippingInputStream extends InputStream
{
    private static final int GZIP_MAGIC = 0x8b1f;
    private static final int TRAILER_SIZE = 8;

    private static final byte[] HEADER = {
        (byte) GZIP_MAGIC,                // Magic number (short)
        (byte)(GZIP_MAGIC >> 8),          // Magic number (short)
        Deflater.DEFLATED,                // Compression method (CM)
        0,                                // Flags (FLG)
        0,                                // Modification time MTIME (int)
        0,                                // Modification time MTIME (int)
        0,                                // Modification time MTIME (int)
        0,                                // Modification time MTIME (int)
        0,                                // Extra flags (XFLG)
        0                                 // Operating system (OS)
    };

    protected final InputStream _in;
    protected final Deflater _def;
    protected final byte[] _buf;
    protected final byte[] _rbuf = new byte[1];
    protected final CRC32 _crc = new CRC32();

    private int _headerOffset;
    private int _trailerOffset;
    private byte[] _trailer;

    private final boolean _usesDefaultDeflater;
    private boolean _closed = false;

    public GZippingInputStream(InputStream in)
    {
        _in = in;
        _def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        _buf = new byte[512];
        _usesDefaultDeflater = true;
        _crc.reset();
    }

    @Override
    public int read() throws IOException
    {
        int n = read(_rbuf);
        if (n < 0) return n;
        return _rbuf[0] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        if (len == 0) return 0;
        int cnt = 0;

        // transfer header
        if (len > 0 && _headerOffset < HEADER.length) {
            int n = Math.min(HEADER.length - _headerOffset, len);
            System.arraycopy(HEADER, _headerOffset, b, off, n);
            cnt += n;
            off += n;
            len -= n;
            _headerOffset += n;
        }

        // copy compressed data
        while (len > 0 && !_def.finished()) {
            // read input
            if (_def.needsInput()) {
                int n = _in.read(_buf);
                if (n < 0) {
                    _def.finish();
                } else if (n > 0) {
                    _crc.update(_buf, 0, n);
                    _def.setInput(_buf, 0, n);
                }
            }

            // compress
            int n = _def.deflate(b, off, len);
            cnt += n;
            off += n;
            len -= n;
        }

        // transfer trailer
        if (len > 0) {
            if (_trailer == null) {
                _trailer = new byte[TRAILER_SIZE];
                putInt32LE((int)_crc.getValue(), _trailer, 0);
                putInt32LE(_def.getTotalIn(), _trailer, 4);
            }
            if (_trailerOffset < _trailer.length) {
                int n = Math.min(_trailer.length - _trailerOffset, len);
                System.arraycopy(_trailer, _trailerOffset, b, off, n);
                cnt += n;
                off += n;
                len -= n;
                _trailerOffset += n;
            }
        }

        if (cnt == 0) {
            assert _headerOffset == HEADER.length;
            assert _def.finished();
            assert _trailerOffset == _trailer.length;
            cnt = -1;
        }
        return cnt;
    }

    private static void putInt32LE(int v, byte[] buf, int offset)
    {
        for (int i = 0; i < 4; ++i, v >>= 8) {
            buf[offset++] = (byte)(v & 0xff);
        }
    }

    @Override
    public void close() throws IOException
    {
        if (!_closed) {
            if (_usesDefaultDeflater) _def.end();
            _in.close();
            _closed = true;
        }
    }

}
