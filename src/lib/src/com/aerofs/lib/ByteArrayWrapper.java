package com.aerofs.lib;

import java.util.Arrays;

/**
 * a wrapper for byte arrays so that the arrays can be used as hash keys
 */
public class ByteArrayWrapper {

    private final byte[] _v;

    public ByteArrayWrapper(byte[] v)
    {
        _v = v;
    }

    public byte[] get()
    {
        return _v;
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(_v);
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && Arrays.equals(_v, ((ByteArrayWrapper) o)._v));
    }
}
