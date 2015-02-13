package com.aerofs.lib.bf;

import java.util.Arrays;

import org.junit.Test;

import com.aerofs.ids.OID;
import com.aerofs.testlib.AbstractTest;

import static org.junit.Assert.*;

public class TestBFHashPartBitSelectOID extends AbstractTest
{

    private BFHashPartBitSelect<OID> bfhash;

    @Test
    public void shouldReturnExpectedIndicesOf11BitField() throws Exception
    {
        bfhash = new BFHashPartBitSelect<OID> (6144,3);
        // - hf indexes partitions of 6144/3 = 2048 bits each
        // - 2048 = 2^11 bits, so the hf selects bit-fields of 11 bits
        // - Create OID that should give hash output of [5, 2, 1]
        //   ie  Ob000000001010000000001000000000001
        //     = Ob 0 00000001 01000000 00010000 00000001
        //     = [0, 1, 64, 16, 1]  as a byte array
        // - Since indexing separate partitions, output will be [4101, 2050, 1]
        byte [] inputBS = {1, 16, 64, 1, 0, 0, 0, 0,
                           0,  0,  0, 0, 0, 0, 0, 0};
        int [] expectIS = {1, 2050, 4101};
        OID oid = new OID(inputBS);

        int [] hashedIS = bfhash.hash(oid);

        assertTrue(Arrays.equals(expectIS, hashedIS));
    }
}
