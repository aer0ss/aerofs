package com.aerofs.lib.id;


/**
 * KIndex: index of conflict branches
 */
public class KIndex extends IntegerID
{
    public static final KIndex KML = new KIndex(-1);
    public static final KIndex MASTER = new KIndex(0);

    public KIndex(int i)
    {
        super(i);
    }

    public KIndex increment()
    {
        return new KIndex(getInt() + 1);
    }
}
