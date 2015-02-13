package com.aerofs.lib.bf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.aerofs.ids.IBFKey;

public class BFHashPartBitSelect<E extends IBFKey> implements IBFHash<E> {

    private final int _k; // Number of hash outputs
    private final int _length; // Length of bit-vector to which this maps
    private final int _mask; // Mask for max value the output hashes can take
    // Width of the subarray of bits to select for hash output
    private final int _selectWidth;

    public BFHashPartBitSelect(int length, int k)
    {
        assert k > 0;
        assert length > 0;
        assert (length % k) == 0;
        assert isPowerOfTwo(length / k);

        _k = k;
        _length = length;
        _mask = (_length / _k) - 1;
        _selectWidth = Integer.numberOfTrailingZeros(_mask + 1);

        // Using ByteBuffer, this hash function can support bit
        // subarrays of at most 24 bits. This indexes a partition of
        // up to 2^24 bits... this is far more than enough for practical BFs
        assert _selectWidth <= 24;
    }

    @Override
    public int length()
    {
        return _length;
    }

    @Override
    public int [] hash(E element)
    {
        // Integer array of return values;
        int [] hashes = new int [_k];
        // ByteBuffer of the key to be hashed _k times
        ByteBuffer bbKey = element.getReadOnlyByteBuffer();
        // In the loop to follow, we expect Integers to be created
        // from the byte array assuming little-endian order.
        bbKey.order(ByteOrder.LITTLE_ENDIAN);

        int startBit = 0;

        for (int i = 0; i < _k; i++) {
            final int startByte = startBit / Byte.SIZE;
            final int bitSelect = (bbKey.getInt(startByte)
                                  >> (startBit % Byte.SIZE)) & _mask;
            hashes[i] = i * (_length / _k) // Offset of partition
                        + bitSelect;       // Index from selected bitfield

            startBit += _selectWidth;

            assert hashes[i] >= 0;
        }

        return hashes;
    }

    private static boolean isPowerOfTwo(int x)
    {
        return ((x != 0) && ((x & (x - 1)) == 0));
    }

}
