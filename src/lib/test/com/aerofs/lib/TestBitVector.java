package com.aerofs.lib;

import junit.framework.Assert;

import org.junit.Test;
import com.aerofs.testlib.AbstractTest;

public class TestBitVector extends AbstractTest
{
    private void assertBitDefined(BitVector bv, int idx, boolean value)
    {
        Assert.assertEquals(value, bv.test(idx));
        Assert.assertEquals(value, bv.test(idx, false));
        Assert.assertEquals(value, bv.test(idx, true));
    }

    private void assertBitUndefined(BitVector bv, int idx)
    {
        Assert.assertEquals(false, bv.test(idx));
        Assert.assertEquals(false, bv.test(idx, false));
        Assert.assertEquals(true, bv.test(idx, true));
    }

    @Test
    public void shouldCreateEmptyVector()
    {
        BitVector bv = new BitVector();
        Assert.assertEquals(0, bv.size());
        assertBitUndefined(bv, 0);
    }

    @Test
    public void shouldCreateAndFillVector()
    {
        BitVector bv = new BitVector(25, true);
        Assert.assertEquals(25, bv.size());
        for (int i = 0; i < 25; ++i)
            assertBitDefined(bv, i, true);
        assertBitUndefined(bv, 26);
    }

    @Test
    public void shouldCreateSmallerVectorFromByteArray()
    {
        byte[] d = new byte[] { ~0x0f, 0x0f };
        BitVector bv = new BitVector(14, d);

        Assert.assertEquals(14, bv.size());
        Assert.assertEquals(d[0], bv.data()[0]);
        Assert.assertEquals(d[1], bv.data()[1]);

        for (int i = 0; i < 4; ++i)
            assertBitDefined(bv, i, false);
        for (int i = 4; i < 12; ++i)
            assertBitDefined(bv, i, true);
        for (int i = 12; i < 14; ++i)
            assertBitDefined(bv, i, false);

        assertBitUndefined(bv, 14);
    }

    @Test
    public void shouldPreserveByteArray()
    {
        byte[] d = new byte[] { ~0x0f, 0x0f };
        BitVector bv = new BitVector(16, d);

        Assert.assertEquals(16, bv.size());
        for (int i = 0; i < 2; ++i)
            Assert.assertEquals(d[i], bv.data()[i]);
    }

    @Test
    public void shouldCreateLargerVectorFromByteArray()
    {
        byte[] d = new byte[] { ~0x0f, 0x0f };
        BitVector bv = new BitVector(18, d);

        Assert.assertEquals(18, bv.size());
        Assert.assertEquals(d[0], bv.data()[0]);
        Assert.assertEquals(d[1], bv.data()[1]);

        for (int i = 0; i < 4; ++i)
            assertBitDefined(bv, i, false);
        for (int i = 4; i < 12; ++i)
            assertBitDefined(bv, i, true);
        for (int i = 12; i < 18; ++i)
            assertBitDefined(bv, i, false);

        assertBitUndefined(bv, 18);
    }

    @Test
    public void shouldCreateVectorFromBooleanSeq()
    {
        BitVector bv = new BitVector(true, true, true, false, false, true, false);

        Assert.assertEquals(7, bv.size());

        for (int i = 0; i < 3; ++i)
            assertBitDefined(bv, i, true);
        for (int i = 3; i < 5; ++i)
            assertBitDefined(bv, i, false);
        assertBitDefined(bv, 5, true);
        assertBitDefined(bv, 6, false);
        assertBitUndefined(bv, 7);
    }

    @Test
    public void shouldGrowAsNeededWhenSetting()
    {
        BitVector bv = new BitVector();
        Assert.assertEquals(0, bv.size());
        assertBitUndefined(bv, 0);
        assertBitUndefined(bv, 10);

        bv.set(10);

        Assert.assertEquals(11, bv.size());
        for (int i = 0; i < 10; ++i)
            assertBitDefined(bv, i, false);

        assertBitDefined(bv, 10, true);
        assertBitUndefined(bv, 11);
    }

    @Test
    public void shouldNotGrowWhenResetting()
    {
        BitVector bv = new BitVector();
        Assert.assertEquals(0, bv.size());
        assertBitUndefined(bv, 0);
        assertBitUndefined(bv, 10);

        bv.reset(10);

        Assert.assertEquals(0, bv.size());
        assertBitUndefined(bv, 0);
        assertBitUndefined(bv, 10);
    }

    @Test
    public void shouldCompareVectors() {
        BitVector a = new BitVector();
        BitVector b = new BitVector();

        Assert.assertEquals(true, a.equals(new BitVector(a)));
        Assert.assertEquals(true, b.equals(new BitVector(b)));
        Assert.assertEquals(true, a.equals(a));
        Assert.assertEquals(true, b.equals(b));
        Assert.assertEquals(true, a.equals(b));
        Assert.assertEquals(true, b.equals(a));

        a.set(1);

        Assert.assertEquals(true, a.equals(new BitVector(a)));
        Assert.assertEquals(true, a.equals(a));
        Assert.assertEquals(false, a.equals(b));
        Assert.assertEquals(false, b.equals(a));

        b.set(9);

        Assert.assertEquals(true, b.equals(new BitVector(b)));
        Assert.assertEquals(true, b.equals(b));
        Assert.assertEquals(false, a.equals(b));
        Assert.assertEquals(false, b.equals(a));

        b.reset(9);
        b.set(1);

        Assert.assertEquals(true, a.equals(b));
        Assert.assertEquals(true, b.equals(a));

        Assert.assertEquals(new BitVector(3, true), new BitVector(3, new byte[] {0x0f}));
    }

    @Test
    public void shouldPerformBitwiseOperations()
    {
        /*
         * Small exception to regular coding style to make the truth tables stand out
         */
        Assert.assertEquals(new BitVector(false, false, false,  true),
                            new BitVector(false, false,  true,  true)
                       .and(new BitVector(false, true, false, true)));

        Assert.assertEquals(new BitVector(false, true, true, false),
                new BitVector(false, false, true, true).xor(
                        new BitVector(false, true, false, true)));

        Assert.assertEquals(new BitVector(false, true, true, true),
                new BitVector(false, false, true, true).or(new BitVector(false, true, false, true)));

        // test operations on vectors of different sizes
        Assert.assertEquals(new BitVector(12, false),
                            new BitVector(12, false)
                       .and(new BitVector(false, true, false, true)));

        Assert.assertEquals(new BitVector(false, true, false, true),
                new BitVector(12, false).xor(new BitVector(false, true, false, true)));

        Assert.assertEquals(new BitVector(false, true, false, true),
                new BitVector(12, false).or(new BitVector(false, true, false, true)));

        Assert.assertEquals(new BitVector(false,  true, false,  true),
                            new BitVector(12, true)
                       .and(new BitVector(false,  true, false,  true)));

        Assert.assertEquals(new BitVector( true, false,  true, false,
                                           true,  true,  true,  true, true, true, true, true),
                            new BitVector(12, true)
                       .xor(new BitVector(false,  true, false,  true)));

        Assert.assertEquals(new BitVector(12, true),
                            new BitVector(12, true)
                        .or(new BitVector(false,  true, false,  true)));

    }

    @Test
    public void shouldFindFirstAndNext()
    {
        BitVector bv = new BitVector(false, true, true, false, false, true, false, false, false);
        Assert.assertEquals(1, bv.findFirstSetBit());
        Assert.assertEquals(1, bv.findNextSetBit(0));
        Assert.assertEquals(1, bv.findNextSetBit(1));
        Assert.assertEquals(2, bv.findNextSetBit(2));
        Assert.assertEquals(5, bv.findNextSetBit(3));
        Assert.assertEquals(5, bv.findNextSetBit(4));
        Assert.assertEquals(5, bv.findNextSetBit(5));
        Assert.assertEquals(-1, bv.findNextSetBit(6));
        Assert.assertEquals(-1, bv.findNextSetBit(7));
        Assert.assertEquals(-1, bv.findNextSetBit(8));
        Assert.assertEquals(-1, bv.findNextSetBit(9));
    }

    @Test
    public void shouldFindLastAndPrevious()
    {
        BitVector bv = new BitVector(false, true, true, false, false, true, false, false, false);
        Assert.assertEquals(5, bv.findLastSetBit());
        Assert.assertEquals(5, bv.findPreviousSetBit(9));
        Assert.assertEquals(5, bv.findPreviousSetBit(8));
        Assert.assertEquals(5, bv.findPreviousSetBit(7));
        Assert.assertEquals(5, bv.findPreviousSetBit(6));
        Assert.assertEquals(5, bv.findPreviousSetBit(5));
        Assert.assertEquals(2, bv.findPreviousSetBit(4));
        Assert.assertEquals(2, bv.findPreviousSetBit(3));
        Assert.assertEquals(2, bv.findPreviousSetBit(2));
        Assert.assertEquals(1, bv.findPreviousSetBit(1));
        Assert.assertEquals(-1, bv.findPreviousSetBit(0));
    }
}
