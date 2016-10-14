package com.aerofs.lib;

/**
 * This class is OBSOLETE. New code should not use it, but should use structures as return values
 * instead.
 */
public class OutArg<T>
{

    private T _v;

    public OutArg()
    {
    }

    public OutArg(T v)
    {
        _v = v;
    }

    public void set(T v)
    {
        _v = v;
    }

    public T get()
    {
        return _v;
    }

    @Override
    public String toString()
    {
        return _v == null ? null : _v.toString();
    }
}
