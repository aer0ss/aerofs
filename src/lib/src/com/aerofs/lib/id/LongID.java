package com.aerofs.lib.id;

import com.aerofs.lib.Util;

public class LongID implements Comparable<LongID> {

    private final long _i;

    protected LongID(long i)
    {
        _i = i;
    }

    @Override
    public int compareTo(LongID arg0)
    {
        return Util.compare(_i, arg0._i);
    }

    public long getLong()
    {
        return _i;
    }

    public String toString()
    {
        return Long.toString(_i);
    }

    @Override
    public boolean equals(Object o)
    {
        return _i == ((LongID) o)._i;
    }

    @Override
    public int hashCode()
    {
        return (int) _i;
    }
}
