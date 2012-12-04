package com.aerofs.lib;

import java.util.Arrays;
import java.util.Iterator;

/**
 * A simple bitvector implementation that grows as needed
 */
public class BitVector implements Iterable<Boolean>
{
    final int BITS_PER_BYTE = 8;

    private int _size;
    private byte[] _d;

    /**
     * Compute number of bytes needed to store a given number of bits.
     * @param size in bits
     * @return minimum number of bytes required to hold the given amount of bits
     */
    private int byteCount(int size)
    {
        // add BITS_PER_BYTE-1 to the input for proper upwards rounding
        return (size + BITS_PER_BYTE - 1) / BITS_PER_BYTE;
    }

    /**
     * grow the underlying byte array
     */
    private void grow(int minSize)
    {
        assert minSize > _size;
        _size = minSize;
        _d = Arrays.copyOf(_d, byteCount(minSize));
    }

    /**
     * Create an empty bit vector
     */
    public BitVector()
    {
        _d = new byte[0];
    }

    /**
     * Create a bit vector and fill it with identical values
     * @param size size in bits of the vector to create
     * @param value default value for bits in the vector
     */
    public BitVector(int size, boolean value)
    {
        assert size >= 0;
        _size = size;
        _d = new byte[byteCount(size)];
        Arrays.fill(_d, value ? (byte)-1 : (byte)0);
        // reset trailing bits in last byte
        final int trailingBits = size % 8;
        if (trailingBits > 0) {
            _d[size / 8] &= (byte)(255 >>> (8 - trailingBits));
        }
    }

    /**
     * Create a bit vector and populate it from a byte array
     * @param size size in bits of the vector to create
     * @param d data used to populate the vector
     *
     * If the given size is larger than the given data, the remaining bits will be reset.
     *
     * The byte->bit mapping in the input array is expected to be the same as what data() returns.
     */
    public BitVector(int size, byte[] d)
    {
        assert size >= 0;
        _size = size;
        _d = Arrays.copyOf(d, byteCount(size));
        // reset trailing bits in last byte
        final int trailingBits = size % 8;
        if (trailingBits > 0) {
            _d[size / 8] &= (byte)(255 >>> (8 - trailingBits));
        }
    }

    /**
     * Create a bit vector and populate it from a sequence of boolean values
     * @param values
     */
    public BitVector(boolean... values)
    {
        _size = values.length;
        _d = new byte[byteCount(_size)];
        Arrays.fill(_d, (byte)0);
        for (int i = 0; i < values.length; ++i) {
            if (values[i]) {
                set(i);
            }
        }
    }

    /**
     * Create a copy of a bit vector
     * @param bv bit vector to copy
     */
    public BitVector(BitVector bv)
    {
        _size = bv._size;
        _d = Arrays.copyOf(bv._d, byteCount(_size));
    }

    /**
     * @return size in bits of the vector
     */
    public int size()
    {
        return _size;
    }

    /**
     * @return the underlying byte array used to store the bit vector
     *
     * vector(abcdefghijklmnop...) -> byte[] { hgfedcba, ponmlkji, ... }
     */
    public byte[] data()
    {
        return _d;
    }

    /**
     * Bit vector comparison
     * NB: vectors of different size can be equal if all the extra bits are reset.
     */
    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof BitVector))
            return false;
        BitVector bv = (BitVector)o;
        int ml = Math.max(_d.length, bv._d.length);
        for (int i = 0; i < ml; ++i)
            if (getByte(i) != bv.getByte(i))
                return false;
        return true;
    }

    @Override
    public String toString()
    {
        StringBuilder bd = new StringBuilder();
        bd.append("BVect(");
        for (int i = 0; i < _size; ++i)
            bd.append(test(i) ? "1" : "0");
        bd.append(")");
        return bd.toString();
    }

    /**
     * @return true if the bit vector is empty (i.e no bits are set)
     */
    public boolean isEmpty()
    {
        for (int i = 0; i < _d.length; ++i)
            if (_d[i] != 0)
                return false;
        return true;
    }

    /**
     * Test a bit in the vector
     * @param idx index of the bit to test
     * @return value of the bit at the given index
     *
     * Negative index values will cause an assert failure (or {@link IndexOutOfBoundsException}
     * if assertions are disabled). Indices larger than the vector size will be evaluated as false.
     */
    public boolean test(int idx)
    {
        return test(idx, false);
    }

    /**
     * Test a bit in the vector
     * @param idx index of the bit to test
     * @param defaultValue value returned for indices larger than vector size
     * @return value of the bit at the given index
     *
     * Negative index values will cause an assert failure (or {@link IndexOutOfBoundsException}
     * if assertions are disabled).
     */
    public boolean test(int idx, boolean defaultValue)
    {
        assert idx >= 0;
        if (idx >= _size) return defaultValue;
        return (_d[idx / BITS_PER_BYTE] & (1 << (idx % BITS_PER_BYTE))) != 0;
    }

    /**
     * Set the value of a bit in the vector
     * @param idx Index of the bit to set
     * @param value new value of the bit
     */
    public void set(int idx, boolean value)
    {
        if (value)
            set(idx);
        else
            reset(idx);
    }

    /**
     * Set a bit of the vector
     * @param idx Index of the bit to set
     */
    public void set(int idx)
    {
        assert idx >= 0;
        if (idx >= _size) grow(idx + 1);
        _d[idx / BITS_PER_BYTE] |= (1 << (idx % BITS_PER_BYTE));
    }

    /**
     * Invert a bit of the vector
     * @param idx Index of the bit to flip
     */
    public void flip(int idx)
    {
        assert idx >= 0;
        if (idx >= _size) grow(idx + 1);
        _d[idx / BITS_PER_BYTE] ^= (1 << (idx % BITS_PER_BYTE));
    }

    /**
     * Reset a bit of the vector
     * @param idx Index of the bit to reset
     */
    public void reset(int idx)
    {
        assert idx >= 0;
        if (idx >= _size) return;
        _d[idx / BITS_PER_BYTE] &= ~(1 << (idx % BITS_PER_BYTE));
    }

    /**
     * In-place bitwise OR of two bitvectors
     */
    public BitVector orInPlace(BitVector bv)
    {
        if (_size < bv._size)
            grow(bv._size);
        int ml = Math.max(_d.length, bv._d.length);
        for (int i = 0; i < ml; ++i) {
            _d[i] |= bv.getByte(i);
        }
        return this;
    }

    /**
     * In-place bitwise AND of two bitvectors
     */
    public BitVector andInPlace(BitVector bv)
    {
        int ml = Math.min(_d.length, bv._d.length);
        for (int i = 0; i < ml; ++i) {
            _d[i] &= bv.getByte(i);
        }
        // shrink the array if possible
        _size = Math.min(_size, bv._size);
        _d = Arrays.copyOf(_d, byteCount(_size));
        return this;
    }

    /**
     * In-place bitwise XOR of two bitvectors
     */
    public BitVector xorInPlace(BitVector bv)
    {
        if (_size < bv._size)
            grow(bv._size);
        int ml = Math.max(_d.length, bv._d.length);
        for (int i = 0; i < ml; ++i) {
            _d[i] ^= bv.getByte(i);
        }
        return this;
    }

    /**
     * @return A bitwise OR of two bitvectors
     */
    public BitVector or(BitVector bv)
    {
        return new BitVector(this).orInPlace(bv);
    }

    /**
     * @return A bitwise AND of two bitvectors
     */
    public BitVector and(BitVector bv)
    {
        return new BitVector(this).andInPlace(bv);
    }

    /**
     * @return A bitwise XOR of two bitvectors
     */
    public BitVector xor(BitVector bv)
    {
        return new BitVector(this).xorInPlace(bv);
    }

    /**
     * @return the index of the first set bit in the vector, or -1 if no bit is set
     */
    public int findFirstSetBit()
    {
        return findNextSetBit(0);
    }

    /**
     * @param idx index from which to look for a set bit (inclusive)
     * @return the index of the next set bit in the vector, or -1 if no bit is set
     */
    public int findNextSetBit(int idx)
    {
        int partial = (getByte(idx / BITS_PER_BYTE) & 0xff) >> (idx % BITS_PER_BYTE);
        if (partial != 0) return idx + getFirstSetBit(partial);

        for (int i = idx / BITS_PER_BYTE + 1; i < _d.length; ++i) {
            int b = getByte(i) & 0xff;
            if (b != 0) return i * BITS_PER_BYTE + getFirstSetBit(b);
        }
        return -1;
    }

    /**
     * @return the index of the last set bit in the vector, or -1 if not bit is set
     */
    public int findLastSetBit()
    {
        return findPreviousSetBit(_size - 1);
    }

    /**
     * @param idx index from which to look for a set bit (inclusive)
     * @return the index of the previous set bit in the vector, or -1 if no bit is set
     */
    public int findPreviousSetBit(int idx)
    {
        int partial = (getByte(idx / BITS_PER_BYTE) & (0xff >> (BITS_PER_BYTE - 1 - (idx % BITS_PER_BYTE))));
        if (partial != 0) return (idx & ~(BITS_PER_BYTE - 1)) + getLastSetBit(partial);

        for (int i = idx / BITS_PER_BYTE - 1; i >= 0; --i) {
            int b = getByte(i) & 0xff;
            if (b != 0) return i * BITS_PER_BYTE + getLastSetBit(b);
        }
        return -1;
    }

    /**
     * Internal helper for operation on bitvectors of different sizes
     */
    private byte getByte(int idx)
    {
        assert idx >= 0;
        return idx < _d.length ? _d[idx] : 0;
    }

    /**
     * Find index of first bit set in a byte
     * @pre b is as one its lower 8 bit set
     */
    private int getFirstSetBit(int b)
    {
        int n = Integer.numberOfTrailingZeros(b);
        assert n < BITS_PER_BYTE : b + " " + n;
        return n;
    }

    /**
     * Find index of last bit set in a byte
     * @pre b is as one its lower 8 bit set
     */
    private int getLastSetBit(int b)
    {
        int n = Integer.numberOfLeadingZeros(b);
        assert n < Integer.SIZE : b + " " + n;
        return (Integer.SIZE - 1) - n;
    }

    /**
     * @return an iterator on the contents of the bitvector
     */
    @Override
    public Iterator<Boolean> iterator()
    {
        final BitVector bv = this;
        return new Iterator<Boolean>() {
            int idx = 0;

            @Override
            public boolean hasNext()
            {
                return idx < bv.size();
            }

            @Override
            public Boolean next()
            {
                return bv.test(idx++);
            }

            @Override
            public void remove()
            {
                throw new UnsupportedOperationException();
            }
        };
    }
}
