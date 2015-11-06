package com.aerofs.daemon.core.collector;

import com.aerofs.base.id.AbstractLongId;

// this index is scoped by the store index
public class SenderFilterIndex extends AbstractLongId<SenderFilterIndex>
{
    public final static SenderFilterIndex BASE = new SenderFilterIndex(0);

    public SenderFilterIndex(long i)
    {
        super(i);
        assert i >= 0;
    }

    SenderFilterIndex plusOne()
    {
        return new SenderFilterIndex(getLong() + 1);
    }
}
