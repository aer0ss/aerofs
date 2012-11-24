/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.id;

public class StringID implements Comparable<StringID>
{
    private final String _i;

    protected StringID(String i)
    {
        _i = i;
    }

    @Override
    public int compareTo(StringID arg0)
    {
        return _i.compareTo(arg0._i);
    }

    @Override
    public String toString()
    {
        return _i;
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && _i.equals(((StringID) o)._i));
    }

    @Override
    public int hashCode()
    {
        return _i.hashCode();
    }
}
