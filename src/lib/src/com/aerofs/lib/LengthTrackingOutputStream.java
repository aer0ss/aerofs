package com.aerofs.lib;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class LengthTrackingOutputStream extends FilterOutputStream {
    long _length;
    boolean _closed;

    public LengthTrackingOutputStream(OutputStream out)
    {
        super(out);
    }

    public long getLength()
    {
        return _length;
    }

    @Override
    public void write(int b) throws IOException
    {
        out.write(b);
        ++_length;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException
    {
        out.write(b, off, len);
        _length += len;
    }
}
