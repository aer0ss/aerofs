package com.aerofs.lib;

import junit.framework.Assert;

import org.junit.Test;

import com.aerofs.testlib.AbstractTest;

public class TestBitVector extends AbstractTest
{
    private void assertBitDefined(BitVector bv, int idx, boolean value) {
        Assert.assertEquals(value, bv.test(idx));
        Assert.assertEquals(value, bv.test(idx, false));
        Assert.assertEquals(value, bv.test(idx, true));
    }

    private void assertBitUndefined(BitVector bv, int idx) {
        Assert.assertEquals(false, bv.test(idx));
        Assert.assertEquals(false, bv.test(idx, false));
        Assert.assertEquals(true, bv.test(idx, true));
    }

    @Test
    public void shouldCreateEmpyVector()
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
}
