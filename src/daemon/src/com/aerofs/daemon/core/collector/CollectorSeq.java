package com.aerofs.daemon.core.collector;

import com.aerofs.lib.id.AbstractLongId;

public class CollectorSeq extends AbstractLongId<CollectorSeq> {

    public CollectorSeq(long i)
    {
        super(i);
    }

    CollectorSeq plusOne()
    {
        return new CollectorSeq(getLong() + 1);
    }
}
