package com.aerofs.lib.bf;

import com.aerofs.lib.id.SID;
import com.google.protobuf.ByteString;

/**
 * The bloom filter for SIDs
 */
public class BFSID extends BloomFilter<SID>
{
    /**
     * Using k=4 and m=256, we can achieve 0.5% false positive rate (P) for
     * 20 stores (n). The equation is:
     *
     * P = ((1-(1-k/m)^n)^k
     */
    public final static BFHashPartBitSelect<SID> HASH =
            new BFHashPartBitSelect<SID>(256, 4);

    public BFSID(BFSID bf)
    {
        super(bf);
    }

    public BFSID(byte[] bs)
    {
        super(bs, HASH);
    }

    public BFSID()
    {
        super(HASH);
    }

    public BFSID(ByteString bs)
    {
        super(bs, HASH);
    }
}
