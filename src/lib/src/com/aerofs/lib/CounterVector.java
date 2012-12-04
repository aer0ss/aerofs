/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.proto.Common.PBCounterVector;
import com.google.protobuf.InvalidProtocolBufferException;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A simple vector of integers that grows as needed and which returns a default value of 0 for
 * absent (positive) indices. It is primarily used to store aggregate sync status (see
 * AggregateSyncStatus in the daemon for more details).
 *
 * This class provides both a naive serialization and a space-efficient one backed by protobuf
 * encoding/decoding.
 *
 * In addition, the counter-oriented API gives a couple of helpers:
 *      increment an element
 *      decrement an element
 *      compare all elements to an integer value, yielding a bitvector
 */
public class CounterVector
{
    private int[] _d;

    /**
     * grow the underlying array
     */
    private void grow(int minSize)
    {
        assert minSize > _d.length;
        _d = Arrays.copyOf(_d, minSize);
    }

    /**
     * Create an empty counter vector
     */
    public CounterVector()
    {
        _d = new int[0];
    }

    /**
     * Create a counter vector from a sequence of initial values
     */
    public CounterVector(int... values)
    {
        _d = Arrays.copyOf(values, values.length);
    }

    /**
     * Create a counter vector from a byte array obtained from {@link #toByteArray}
     */
    public CounterVector(@Nonnull byte[] values)
    {
        assert values.length % 4 == 0;
        _d = new int[values.length / 4];
        ByteBuffer buf = ByteBuffer.wrap(values);
        for (int i = 0; i < _d.length; ++i) {
            _d[i] = buf.getInt(4 * i);
        }
    }

    /**
     * Creates a counter vector by copying the contents of an existing one
     */
    public CounterVector(CounterVector cv)
    {
        _d = Arrays.copyOf(cv._d, cv._d.length);
    }

    /**
     * Create a counter vector from a byte array obtained from {@link #toByteArrayCompressed}
     * @throws IllegalArgumentException if the input is not a valid PBCounterVector encoding
     */
    public static CounterVector fromByteArrayCompressed(@Nonnull byte[] compressed)
    {
        CounterVector cv = new CounterVector();
        try {
            PBCounterVector pbcv = PBCounterVector.parseFrom(compressed);
            cv._d = new int[pbcv.getCounterCount()];
            for (int i = 0; i < cv._d.length; ++i) cv._d[i] = pbcv.getCounter(i);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException(e);
        }
        return cv;
    }

    /**
     * Counter vector comparison
     * NB: vectors of different size can be equal if all the trailing values are zero.
     */
    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof CounterVector))
            return false;
        CounterVector cv = (CounterVector)o;
        int ml = Math.max(_d.length, cv._d.length);
        for (int i = 0; i < ml; ++i)
            if (get(i) != cv.get(i))
                return false;
        return true;
    }

    @Override
    public String toString()
    {
        StringBuilder bd = new StringBuilder();
        bd.append("CVect(");
        for (int i = 0; i < _d.length; ++i) {
            if (i > 0) bd.append(", ");
            bd.append(_d[i]);
        }
        bd.append(")");
        return bd.toString();
    }

    /**
     * Serialize the contents of the counter vector into a byte array using protobuf
     * variable size encoding through {@link PBCounterVector}
     */
    public byte[] toByteArrayCompressed()
    {
        PBCounterVector.Builder builder = PBCounterVector.newBuilder();
        for (int i = 0; i < _d.length; ++i) {
            builder.addCounter(_d[i]);
        }
        return builder.build().toByteArray();
    }

    /**
     * Serialize the contents of the counter vector into a byte array using a simple fixed-size
     * encoding
     */
    public byte[] toByteArray()
    {
        ByteBuffer buf = ByteBuffer.allocate(_d.length * 4);
        for (int i = 0; i < _d.length; ++i) {
            buf.putInt(_d[i]);
        }
        return buf.array();
    }

    /**
     * @return number of counters in the vector
     */
    public int size()
    {
        return _d.length;
    }

    /**
     * @return the value of counter {@code idx}, or 0 if the counter is not present
     */
    public int get(int idx)
    {
        assert idx >= 0;
        return idx < _d.length ? _d[idx] : 0;
    }

    /**
     * @return the value of counter {@code idx}, or {@code defaultValue} if the counter is absent
     */
    public int get(int idx, int defaultValue)
    {
        assert idx >= 0;
        return idx < _d.length ? _d[idx] : defaultValue;
    }

    /**
     * Set counter {@code idx} to {@code value}
     */
    public void set(int idx, int value)
    {
        assert idx >= 0;
        if (idx >= _d.length) grow(idx + 1);
        _d[idx] = value;
    }

    /**
     * Increment counter {@code idx}
     */
    public void inc(int idx)
    {
        assert idx >= 0;
        if (idx >= _d.length) grow(idx + 1);
        ++_d[idx];
    }

    /**
     * Decrement counter {@code idx}
     */
    public void dec(int idx)
    {
        assert idx >= 0;
        if (idx >= _d.length) grow(idx + 1);
        --_d[idx];
    }

    /**
     * Compare {@code n} counters in the vector to a given {@code value}
     * @param n number of counters to compare, can be smaller or larger than vector size
     * @return a bitvector where bit {@code i} corresponds to the result of {@code get(i) == value}
     *
     * Note: when comparing to 0, only the first {@code n} element of the resulting vector can be
     * relied on.
     */
    public BitVector elementsEqual(int value, int n)
    {
        BitVector bv = new BitVector(n, false);
        for (int i = 0; i < n; ++i) {
            if (get(i) == value) bv.set(i);
        }
        return bv;
    }
}
