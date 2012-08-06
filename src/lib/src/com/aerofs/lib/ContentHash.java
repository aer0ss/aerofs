package com.aerofs.lib;

import com.google.protobuf.ByteString;

import java.util.Arrays;

/**
 * This class represents content hash of a file. Its value is a concatenation of one or more file
 * block hashes.
 */
public class ContentHash
{
    /**
     * The byte count of a single block hash value
     */
    public static final int UNIT_LENGTH = SecUtil.newMessageDigest().getDigestLength();

    private final byte[] _hash;
    private ByteString _pb;

    public ContentHash(byte[] hash)
    {
        assert hash.length % UNIT_LENGTH == 0;

        _hash = hash;
    }

    public ContentHash(ByteString pb)
    {
        this(pb.toByteArray());
        _pb = pb;
    }

    public ByteString toPB()
    {
        if (_pb == null) _pb = ByteString.copyFrom(_hash);

        return _pb;
    }

    public byte[] getBytes()
    {
        return _hash;
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(_hash);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof ContentHash)) return false;

        ContentHash h2 = (ContentHash) other;
        return Arrays.equals(_hash, h2._hash);
    }

    @Override
    public String toString()
    {
        return toHex();
    }

    public String toHex()
    {
        return Util.hexEncode(getBytes());
    }
}
