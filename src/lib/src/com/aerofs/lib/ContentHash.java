package com.aerofs.lib;

import com.aerofs.base.BaseUtil;
import com.google.protobuf.ByteString;
import com.google.protobuf.LeanByteString;

import java.util.Arrays;

/**
 * This class represents content hash of a file. Its value is a concatenation of one or more file
 * block hashes.
 */
public class ContentHash extends LeanByteString
{
    /**
     * The byte count of a single block hash value
     */
    public static final int UNIT_LENGTH = SecUtil.newMessageDigest().getDigestLength();

    public ContentHash(byte[] hash)
    {
        super(hash);
        assert hash.length % UNIT_LENGTH == 0;
    }

    public ContentHash(ByteString pb)
    {
        super(pb);
    }

    public ByteString toPB()
    {
        return this;
    }

    public byte[] getBytes()
    {
        return getInternalByteArray();
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(getBytes());
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof ContentHash)) return false;

        ContentHash h2 = (ContentHash) other;
        return Arrays.equals(getBytes(), h2.getBytes());
    }

    @Override
    public String toString()
    {
        return toHex();
    }

    public String toHex()
    {
        return BaseUtil.hexEncode(getBytes());
    }
}
