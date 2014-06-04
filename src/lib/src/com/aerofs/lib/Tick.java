package com.aerofs.lib;

import static com.google.common.base.Preconditions.checkState;

// TODO extends Long
public class Tick {

    public static final Tick ZERO = new Tick(0);

    private final long _l;

    public Tick(long l)
    {
        _l = l;
    }

    public long getLong()
    {
        return _l;
    }

    public String toString()
    {
        return Long.toString(getLong());
    }

    // For alias update, odd tick should be assigned.
    public Tick incAlias()
    {
        long l = (_l & 0x1) == 0 ? _l + 1 : _l + 2;
        checkState(l % 2 == 1);

        return new Tick(l);
    }

    // For non-alias update, even tick should be assigned.
    public Tick incNonAlias()
    {
        long l = (_l & 0x1) == 0 ? _l + 2 : _l + 1;
        checkState(l % 2 == 0);

        return new Tick(l);
    }

    public boolean isAlias()
    {
        return (_l & 0x1) == 1;
    }

    @Override
    public int hashCode()
    {
        return (int) _l;
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && _l == ((Tick) o)._l);
    }
}
