package com.aerofs.lib;

public class InOutArg<T> {

    private T _v;

    public InOutArg(T v)
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
        return _v.toString();
    }
}
