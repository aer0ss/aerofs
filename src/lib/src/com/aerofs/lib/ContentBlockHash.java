package com.aerofs.lib;

import com.aerofs.base.BaseUtil;
import com.google.protobuf.ByteString;
import com.google.protobuf.LeanByteString;

import java.util.Arrays;

/**
 * Block-based content hash.
 *
 * It used to be used for all content hashes in AeroFS but is now only used by BlockStorage.
 *
 * A ContentHash represents the hash of one or more blocks of a file. Block-based hashing is
 * used instead of whole-file hashing because BlockStorage splits files in 4Mb chunks and
 * addresses them by their content hash.
 */
public class ContentBlockHash extends LeanByteString
{
    /**
     * The byte count of a single block hash value
     */
    public static final int UNIT_LENGTH = SecUtil.newMessageDigest().getDigestLength();

    public ContentBlockHash(byte[] hash)
    {
        super(hash);
        assert hash.length % UNIT_LENGTH == 0;
    }

    public ContentBlockHash(ByteString pb)
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
        if (!(other instanceof ContentBlockHash)) return false;

        ContentBlockHash h2 = (ContentBlockHash) other;
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
