/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio;

import com.aerofs.zephyr.core.BufferPool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class MultiByteBufferInputStream extends InputStream
{
    public MultiByteBufferInputStream(ByteBuffer[] bufs, BufferPool owner)
    {
        _bufs = bufs;
        _owner = owner;
        _curbuf = 0;
    }

    @Override
    public synchronized int read()
        throws IOException
    {
        if (_curbuf == _bufs.length) return -1;

        int retbyte = _bufs[_curbuf].get() & 0xFF;
        if (!_bufs[_curbuf].hasRemaining()) ++_curbuf;

        return retbyte;
    }

    @Override
    public synchronized int read(byte[] bytes, int offset, int length)
        throws IOException
    {
        int readbytes = -1;
        while ((readbytes < length) && (_curbuf < _bufs.length)) {
            int avail = (
                length > _bufs[_curbuf].remaining() ?
                    _bufs[_curbuf].remaining() :
                    length);

            _bufs[_curbuf].get(bytes, offset, avail);
            offset += avail;

            if (!_bufs[_curbuf].hasRemaining()) ++_curbuf;
        }

        return readbytes;
    }

    //
    // [sigh] I'm pretty sure this isn't a good idea
    //
    @Override
    protected void finalize() throws Throwable
    {
        if (_owner != null) {
            for (ByteBuffer b : _bufs) {
                _owner.putBuffer_(b);
            }
        }

        super.finalize();
    }

    private ByteBuffer[] _bufs;
    private BufferPool _owner;
    private int _curbuf;
}
