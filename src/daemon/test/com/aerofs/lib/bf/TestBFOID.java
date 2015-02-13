package com.aerofs.lib.bf;

import org.junit.Test;

import com.aerofs.ids.OID;
import com.aerofs.testlib.AbstractTest;

import static org.junit.Assert.*;

public class TestBFOID extends AbstractTest
{

    private BFHashPartBitSelect<OID> bfhash;
    private BloomFilter<OID> bf;
    private BloomFilter<OID> bfunion;


    private final byte [] inputBS = {1, 16, 64, 1, 0, 9, 0, 62,
                                     0, 41, 0, 7, 8, 126, 15, 0};
    private final OID oid = new OID(inputBS);


    @Test
    public void shouldContainOIDAfterInsertingIt_3x2048()
    {
        /*
         * Arbitrarily test a Bloom filter of 3 partitions with 2048 bits each
         * The BF thus has 3*2048 = 6144 total bits
         */
        bfhash = new BFHashPartBitSelect<OID> (6144,3);
        bf = new BloomFilter<OID>(bfhash);

        shouldContainOIDAfterInsertingIt(oid);
    }

    @Test
    public void shouldContainOIDAfterUnionWithContainingBloomFilter_5x4096()
    {
        /*
         * Arbitrarily test a Bloom filter of 5 partitions with 4096 bits each
         * The BF thus has 5*4096 = 20480 total bits
         */
        bfhash = new BFHashPartBitSelect<OID> (20480,5);
        bf = new BloomFilter<OID>(bfhash);
        bfunion = new BloomFilter<OID>(bfhash);

        shouldContainOIDAfterUnionWithContainingBloomFilter(oid);
    }

    private void shouldContainOIDAfterInsertingIt(OID oid)
    {
        bf.add_(oid);
        assertTrue(bf.contains_(oid));
    }

    private void shouldContainOIDAfterUnionWithContainingBloomFilter(OID oid)
    {
        bf.add_(oid);
        bfunion.union_(bf);

        assertFalse(bfunion.isEmpty_());
        /*
         * Assert that the *unioned* Bloom filter contains
         * the oid inserted into the other
         */
        assertTrue(bfunion.contains_(oid));
    }
}
