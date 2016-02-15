package com.aerofs.lib.bf;

import java.util.Arrays;

import com.aerofs.base.BaseUtil;
import com.google.protobuf.ByteString;

/**
 * Implementation of a Bloom filter
 * @param <E> Object type that is to be inserted into the Bloom filter,
 * e.g. String or Integer.
 */
public class BloomFilter<E> {
    private final static int BYTE_SIZE = Byte.SIZE;

    private final byte[] _bs; // byte array
    private final IBFHash<E> _hf; // collection of hash functions
    private boolean _finalized; // for debugging only, to prevent finalized
                                // filters to be modified in the future
    private ByteString _pb;     // a cached value. invalidated on updates

    /**
      * Constructs an empty Bloom filter.
      *
      * @param bfhash a Hash function class
      */
    public BloomFilter(IBFHash<E> bfhash)
    {
        _hf = bfhash;
        _bs = new byte[length() / BYTE_SIZE];
        assert null != _bs;
    }

    public BloomFilter(byte[] bs, IBFHash<E> bfhash)
    {
        _hf = bfhash;
        _bs = bs;
        assert _bs.length * BYTE_SIZE == length();
    }

    public BloomFilter(ByteString pb, IBFHash<E> bfhash)
    {
        this(pb.toByteArray(), bfhash);
        _pb = pb;
    }

    /**
     * copy constructor
     */
    public BloomFilter(BloomFilter<E> bf)
    {
        _hf = bf._hf;
        _bs = Arrays.copyOf(bf._bs, bf._bs.length);
    }

    /**
     * @return true if one or more bits are updated
     * N.B. returning false doesn't necessarily mean that the element already
     * exists in the filter
     */
    public boolean add_(E element)
    {
        /* Record the multiple hashes of the element */
        int [] indexes = _hf.hash(element);

        return add_(indexes);
    }

    /**
     * @return true if one or more bits are updated
     * N.B. returning false doesn't necessarily mean that the element already
     * exists in the filter
     */
    public boolean add_(int [] indexes)
    {
        assert indexes.length > 0;

        /* Set the bit indexed by each hash */
        boolean updated = false;
        for (int idx : indexes) {
            assert (idx >= 0 && idx < length());
            if (setBit_(idx)) updated = true;
        }

        if (updated) _pb = null;
        return updated;
    }

    /**
     * set the filter to all one's
     */
    public void addAll_()
    {
        assert !_finalized;

        Arrays.fill(_bs, (byte) 0xFF);
        _pb = null;
    }

    /**
     * Union with Bloom filter bit-vector parameter
     * This BloomFilter is overwritten with the union result.
     * N.B. returning false doesn't necessarily mean that the elements
     * contained in bf already exist in 'this' filter.
     * @param bf Bloom filter from which to union with this
     * @return true if one or more bits are updated
     */
    public boolean union_(BloomFilter<E> bf)
    {
        assert !_finalized;
        assert _hf == bf._hf
           : "Unioned Bloom filter must have identical hash object to this one";
        assert _bs.length == bf._bs.length;

        // TODO: there may be a faster way to do this in Java
        // e.g. ORing 32-bit or 64-bit words together would be more
        //      efficient.
        boolean updated = false;
        for (int i = 0; i < _bs.length; i++) {
            byte before = _bs[i];
            byte after = (byte) (before | bf._bs[i]);
            if (before != after) {
                _bs[i] = after;
                updated = true;
            }
        }

        if (updated) _pb = null;
        return updated;
    }

    public boolean contains_(E element)
    {
        /* Record the multiple hashes of the element */
        int [] indexes = _hf.hash(element);

        return contains_(indexes);
    }

    public boolean contains_(int [] indexes)
    {
        assert indexes.length > 0;

        /* Test each bit indexed by the hashes
         * If any of these bits is zero, there is no match
         */
        for (int idx : indexes) {
            assert (idx >= 0 && idx < length());
            if (false == testBit_(idx)) {
                return false;
            }
        }
        /* All indexed bits were set to one, so the element matches*/
        return true;
    }

    /**
     * @return total number of bits in the Bloom filter
     */
    public int length()
    {
        return _hf.length();
    }

    public byte[] getBytes()
    {
        return _bs;
    }

    public boolean isEmpty_()
    {
        for (byte b : _bs) if (b != 0) return false;
        return true;
    }

    public void finalize_()
    {
        _finalized = true;
    }

    /**
     * @return true if one or more bits are updated
     */
    private boolean setBit_(int idx)
    {
        assert !_finalized;
        final int block = idx / BYTE_SIZE;
        final int offset = idx % BYTE_SIZE;
        assert block < _bs.length;
        final byte before = _bs[block];
        final byte after = (byte) (before | (1 << offset));

        if (before != after) {
            _bs[block] = after;
            return true;
        } else {
            return false;
        }
    }

    private boolean testBit_(int idx)
    {
        final int block = idx / BYTE_SIZE;
        final int offset = idx % BYTE_SIZE;
        assert block < _bs.length;
        return !(0 == (byte) (_bs[block] & (1 << offset)));
    }

    @Override
    public String toString()
    {
        return BaseUtil.hexEncode(_bs).replace('0', '_');
    }

    public ByteString toPB()
    {
        if (_pb == null) _pb = ByteString.copyFrom(_bs);
        return _pb;
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(_bs);
    }

    @Override
    public boolean equals(Object o)
    {
        return o == this || (o != null && Arrays.equals(_bs,
                ((BloomFilter<?>) o)._bs));
    }
}
