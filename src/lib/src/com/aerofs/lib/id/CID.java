package com.aerofs.lib.id;

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
