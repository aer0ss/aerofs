/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.testlib.AbstractTest;
import junit.framework.Assert;
import org.junit.Test;

public class TestCounterVector extends AbstractTest
{
    private void assertCounterDefined(CounterVector cv, int idx, int value)
    {
        Assert.assertEquals(value, cv.get(idx));
        Assert.assertEquals(value, cv.get(idx, 0));
        Assert.assertEquals(value, cv.get(idx, value));
    }

    private void assertCounterUndefined(CounterVector cv, int idx)
    {
        Assert.assertEquals(0, cv.get(idx));
        Assert.assertEquals(0, cv.get(idx, 0));
        Assert.assertEquals(1, cv.get(idx, 1));
    }

    private void assertCounterVectorDefined(CounterVector cv, int... values)
    {
        for (int i = 0; i < values.length; ++i) {
            assertCounterDefined(cv, i, values[i]);
        }
    }

    private void assertSerializationIsInvertible(CounterVector cv)
    {
        Assert.assertEquals(cv, new CounterVector(cv.toByteArray()));
        Assert.assertEquals(cv, CounterVector.fromByteArrayCompressed(cv.toByteArrayCompressed()));
    }

    @Test
    public void shouldCreateEmptyVector()
    {
        CounterVector cv = new CounterVector();
        Assert.assertEquals(0, cv.size());
        assertCounterUndefined(cv, 0);
    }

    @Test
    public void shouldCreateVectorFromSeq()
    {
        CounterVector cv = new CounterVector(1, 1, 0, 42, -3, 0, 0, 0, 5);
        Assert.assertEquals(9, cv.size());
        assertCounterVectorDefined(cv, 1, 1, 0, 42, -3, 0, 0, 0, 5);
        assertCounterUndefined(cv, 9);
    }

    @Test
    public void shouldGrowAsNeededWhenIncrementing()
    {
        CounterVector cv = new CounterVector();
        Assert.assertEquals(0, cv.size());
        cv.inc(3);
        assertCounterVectorDefined(cv, 0, 0, 0, 1);
        assertCounterUndefined(cv, 4);
    }

    @Test
    public void shouldGrowAsNeededWhenDecrementing()
    {
        CounterVector cv = new CounterVector();
        Assert.assertEquals(0, cv.size());
        cv.dec(3);
        assertCounterVectorDefined(cv, 0, 0, 0, -1);
        assertCounterUndefined(cv, 4);
    }

    @Test
    public void shouldCompareEqualityOfVectors()
    {
        CounterVector a = new CounterVector();
        CounterVector b = new CounterVector();

        Assert.assertTrue(a.equals(b));
        Assert.assertTrue(b.equals(a));
        Assert.assertTrue(a.equals(a));
        Assert.assertTrue(b.equals(b));

        a.set(3, 0);

        Assert.assertTrue(a.equals(b));
        Assert.assertTrue(b.equals(a));
        Assert.assertTrue(a.equals(a));
        Assert.assertTrue(b.equals(b));

        b.set(0, 1);

        Assert.assertFalse(a.equals(b));
        Assert.assertFalse(b.equals(a));
        Assert.assertTrue(a.equals(a));
        Assert.assertTrue(b.equals(b));

        a.set(0, 1);

        Assert.assertTrue(a.equals(b));
        Assert.assertTrue(b.equals(a));
        Assert.assertTrue(a.equals(a));
        Assert.assertTrue(b.equals(b));
    }

    @Test
    public void shouldCompareElements()
    {
        Assert.assertEquals(new BitVector(), new CounterVector().elementsEqual(0, 0));
        Assert.assertEquals(new BitVector(12, true), new CounterVector().elementsEqual(0, 12));

        Assert.assertEquals(new BitVector(), new CounterVector().elementsEqual(15, 0));
        Assert.assertEquals(new BitVector(), new CounterVector().elementsEqual(15, 10));

        Assert.assertEquals(new BitVector(),
                            new CounterVector(0, 1, 1, 2, 5, 0, 2).elementsEqual(1, 0));
        Assert.assertEquals(new BitVector(),
                            new CounterVector(0, 1, 1, 2, 5, 0, 2).elementsEqual(2, 0));
        Assert.assertEquals(new BitVector(true, false, false, false, false, true, false, true, true),
                            new CounterVector(0, 1, 1, 2, 5, 0, 2).elementsEqual(0, 9));
        Assert.assertEquals(new BitVector(false, true, true),
                            new CounterVector(0, 1, 1, 2, 5, 0, 2).elementsEqual(1, 9));
        Assert.assertEquals(new BitVector(false, false, false, true, false, false, true),
                            new CounterVector(0, 1, 1, 2, 5, 0, 2).elementsEqual(2, 9));
    }

    @Test
    public void shouldSerializeDeserialize()
    {
        assertSerializationIsInvertible(new CounterVector());
        assertSerializationIsInvertible(new CounterVector(1));
        assertSerializationIsInvertible(new CounterVector(1 << 30));
        assertSerializationIsInvertible(new CounterVector(-1));
        assertSerializationIsInvertible(new CounterVector(-(1 << 30)));
        assertSerializationIsInvertible(new CounterVector(0, 0, 1, 0, 2, 3, 1, 0));
    }
}
