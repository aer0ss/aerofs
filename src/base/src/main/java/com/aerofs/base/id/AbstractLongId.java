package com.aerofs.base.id;

import com.aerofs.base.BaseUtil;

public abstract class AbstractLongId<T extends AbstractLongId<T>> implements Comparable<T>
{
    private final long _id;

    protected AbstractLongId(long id)
    {
        _id = id;
    }

    public long getLong()
    {
        return _id;
    }

    public int compareTo(T o)
    {
        return BaseUtil.compare(_id, ((AbstractLongId)o)._id);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        return getClass().equals(obj.getClass()) && _id == ((AbstractLongId)obj)._id;
    }

    @Override
    public int hashCode()
    {
        return (int)(_id ^ (_id >>> 32));
    }

    public String toString()
    {
        return String.valueOf(_id);
    }
}
