package com.aerofs.lib.id;

import com.aerofs.base.id.IntegerID;

/**
 * Component ID
 */
public class CID extends IntegerID
{
    public static final CID META    = new CID(0);
    public static final CID CONTENT = new CID(1);

    public CID(int i)
    {
        super(i);
    }

    public boolean isMeta()
    {
        return equals(META);
    }

    public boolean isContent()
    {
        return equals(CONTENT);
    }
}
