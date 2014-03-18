/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib;

import com.aerofs.base.BaseUtil;
import com.google.protobuf.ByteString;
import com.google.protobuf.LeanByteString;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

public class ContentHash extends LeanByteString
{
    public static final int LENGTH = SecUtil.newMessageDigest().getDigestLength();

    public ContentHash(byte[] hash)
    {
        super(hash);
        checkArgument(hash.length == LENGTH);
    }

    public ContentHash(ByteString pb)
    {
        super(pb);
        checkArgument(pb.size() == LENGTH);
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
