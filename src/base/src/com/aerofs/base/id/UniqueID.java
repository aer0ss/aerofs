package com.aerofs.base.id;

import java.nio.ByteBuffer;
import java.util.UUID;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.bf.IBFKey;
import com.aerofs.base.ex.ExFormatError;
import com.google.protobuf.ByteString;
import com.google.protobuf.LeanByteString;

// globally unique ids.
//
// templatizing this class is not that easy because of static member ZERO here
//
public class UniqueID extends LeanByteString implements Comparable<UniqueID>, IBFKey
{
    public static final int LENGTH = 16;

    /**
     * UniqueID obtained from generate() are UUID version 4 as specified by RFC 4122
     *
     * To distinguish different subtypes of unique ids we sometimes change the value of the version
     * nibble (4 most significant bits of the 7th byte of the id).
     * The following constants help manipulating the 4 bits in question.
     */
    public static final int VERSION_BYTE = 6;
    public static final int VERSION_MASK = 0xf0;
    public static final int VERSION_SHIFT = 4;

    public static int getVersionNibble(byte[] bs)
    {
        assert bs.length == LENGTH;
        return (bs[VERSION_BYTE] & VERSION_MASK) >> VERSION_SHIFT;
    }

    public static void setVersionNibble(byte[] bs, int value)
    {
        assert bs.length == LENGTH;
        assert value >= 0 && value < 16;
        bs[VERSION_BYTE] &= ~VERSION_MASK;
        bs[VERSION_BYTE] |= value << VERSION_SHIFT;
    }

    protected int getVersionNibble()
    {
        return getVersionNibble(getInternalByteArray());
    }

    /**
     * UniqueID subclasses may restrict the range of valid values. When such IDs are constructed
     * from internal values, asserts should be used to enforce the restrictions. However, when such
     * IDs are received from the outside the network, it should be possible to simply ignore invalid
     * IDs to avoid DoS by remote peers so exceptions should be prefered in that case.
     */
    public static class ExInvalidID extends Exception
    {
        private static final long serialVersionUID = 0L;
    }

    public static final UniqueID ZERO = new UniqueID(new byte[LENGTH]);

    private static byte[] hexDecodeID(String str, int start, int end) throws ExFormatError
    {
        byte[] bs = BaseUtil.hexDecode(str, start, end);
        if (bs.length != LENGTH) throw new ExFormatError("wrong length");
        return bs;
    }

    public static UniqueID generate()
    {
        UUID uuid = UUID.randomUUID();

        long v = uuid.getLeastSignificantBits();
        byte [] bs = new byte[LENGTH];
        for (int i = 0; i < 8; i++) {
            bs[LENGTH - 1 - i] = (byte)(v >>> (i * 8));
        }

        v = uuid.getMostSignificantBits();
        for (int i = 0; i < 8; i++) {
            bs[LENGTH - 8 - 1 - i] = (byte)(v >>> (i * 8));
        }

        /**
         * The output of this code should be a version 4 UUID as specified by RFC 4122
         *
         * version 4 UUID as hex string: xxxxxxxxxxxxMxxxNxxxxxxxxxxxxxxx
         * invariant 1: M = 4
         * invariant 2: N in {8, 9, a, b}
         *
         * We rely on some of the 6 fixed bits specified by said RFC to distinguish various subtypes
         * of unique ids so we assert that they are set as expected.
         */
        assert getVersionNibble(bs) == 4 : BaseUtil.hexEncode(bs);
        assert (bs[VERSION_BYTE + 2] & 0xc0) == 0x80 : BaseUtil.hexEncode(bs);

        return new UniqueID(bs);
    }

    /**
     *  Convert a string representation of a UniqueID, generated by UniqueID.toStringFormal, to a
     *  UniqueID.
     */
    public UniqueID(String str) throws ExFormatError
    {
        this(str, 0, str.length());
    }

    /**
     *  Convert a string representation of a UniqueID, generated by UniqueID.toStringFormal, to a
     *  UniqueID. {@code start} and {@code end} specifies the start and end point of the substring
     *  to be parse in {@code str}.
     */
    public UniqueID(String str, int start, int end) throws ExFormatError
    {
        this(hexDecodeID(str, start, end));
    }

    /**
     * Wrap the given byte array (NB: it is NOT copied)
     */
    public UniqueID(byte[] bs)
    {
        super(bs);
        assert bs.length == LENGTH : BaseUtil.hexEncode(bs);
    }

    /**
     * Create an id from a ByteString (shares the underlying byte array if possible)
     */
    public UniqueID(ByteString bstr)
    {
        super(bstr);
    }

    @Override
    public String toString()
    {
        return toStringImpl('<', 3, '>');
    }

    protected String toStringImpl(char pre, int nbytes, char post)
    {
        char[] str = new char[2 + nbytes * 2];
        str[0] = pre;
        BaseUtil.hexEncode(getInternalByteArray(), 0, nbytes, str, 1);
        str[str.length - 1] = post;
        return new String(str);
    }

    public String toStringFormal()
    {
        return BaseUtil.hexEncode(getInternalByteArray(), 0, LENGTH);
    }

    public static UniqueID fromStringFormal(String hex) throws ExFormatError
    {
        return new UniqueID(hex, 0, hex.length());
    }

    @Override
    public ByteBuffer getReadOnlyByteBuffer()
    {
        return toPB().asReadOnlyByteBuffer();
    }

    /**
     * IMPORTANT: Do not modify the returned byte array directly but work on a copy instead.
     */
    public byte[] getBytes()
    {
        return getInternalByteArray();
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && super.equals(o));
    }

    @Override
    public int hashCode()
    {
        // UUIDs are pseudo-random numbers so selecting any 32 bits gives as good a hash function
        // as pretty much any non-cryptographycally secure byte array hash
        // NB: for this to be true we should not select bits from the version nibble
        return getReadOnlyByteBuffer().getInt(0);
    }

    @Override
    public int compareTo(UniqueID id)
    {
        final byte [] bs0 = getInternalByteArray();
        final byte [] bs1 = id.getInternalByteArray();
        assert bs0.length == LENGTH && bs1.length == LENGTH;

        for (int i = LENGTH - 1; i >= 0; i--) {
            int diff = bs0[i] - bs1[i];
            if (diff != 0) return diff;
        }
        return 0;
    }

    public ByteString toPB()
    {
        return this;
    }
}
