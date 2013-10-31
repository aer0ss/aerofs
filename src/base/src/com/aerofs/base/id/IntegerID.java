package com.aerofs.base.id;

import com.aerofs.base.BaseUtil;

public class IntegerID implements Comparable<IntegerID> {

    private final int _i;

    protected IntegerID(int i)
    {
        _i = i;
    }

    @Override
    public int compareTo(IntegerID arg0)
    {
        return BaseUtil.compare(_i, arg0._i);
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
