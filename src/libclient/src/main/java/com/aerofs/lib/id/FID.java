package com.aerofs.lib.id;

import java.util.Arrays;

import com.aerofs.base.BaseUtil;

/**
 * Persistent file identifiers specific to OSes and filesystems (e.g. i-node number)
 */
public class FID implements Comparable<FID>
{
    private final byte[] _bs;

    public FID(byte[] bs)
    {
        _bs = bs;
    }

    @Override
    public int compareTo(FID arg0)
    {
        for (int i = 0; i < _bs.length; i++) {
            int diff = _bs[i] - arg0._bs[i];
            if (diff != 0) return diff;
        }
        return 0;
    }

    public byte[] getBytes()
    {
        return _bs;
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && Arrays.equals(_bs, ((FID) o)._bs));
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(_bs);
    }

    @Override
    public String toString()
    {
        return BaseUtil.hexEncode(_bs);
    }
}
