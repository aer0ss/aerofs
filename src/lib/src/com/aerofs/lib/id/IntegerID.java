package com.aerofs.lib.id;

import com.aerofs.lib.Util;

public class IntegerID implements Comparable<IntegerID> {

    private final int _i;

    protected IntegerID(int i)
    {
        _i = i;
    }

    @Override
    public int compareTo(IntegerID arg0)
    {
        return Util.compare(_i, arg0._i);
    }

    public int getInt()
    {
        return _i;
    }

    @Override
    public String toString()
    {
        return Integer.toString(getInt());
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && _i == ((IntegerID) o)._i);
    }

    @Override
    public int hashCode()
    {
        return _i;
    }
}
