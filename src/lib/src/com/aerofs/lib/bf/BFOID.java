package com.aerofs.lib.bf;

import com.aerofs.ids.OID;
import com.google.protobuf.ByteString;

/**
 * The bloom filter for OIDs
 */
public class BFOID extends BloomFilter<OID>
{
    /**
     * Using k=4 and m=1024, we can achieve 1% false positive rate (P) for
     * 100 files (n). The equation is:
     *
     * P = ((1-(1-k/m)^n)^k
     */
    public final static BFHashPartBitSelect<OID> HASH =
            new BFHashPartBitSelect<OID>(1024, 4);

    public BFOID(BFOID bf)
    {
        super(bf);
    }

    public BFOID(byte[] bs)
    {
        super(bs, HASH);
    }

    public BFOID()
    {
        super(HASH);
    }

    public BFOID(ByteString bs)
    {
        super(bs, HASH);
    }

    public static BFOID of(OID... objects)
    {
        BFOID bf = new BFOID();
        for (OID o : objects) bf.add_(o);
        return bf;
    }
}
