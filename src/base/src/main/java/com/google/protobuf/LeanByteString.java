/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.google.protobuf;

import java.util.Arrays;

/**
 * Because F*** You Google for thinking you know better
 *
 * This class is a dummy whose only purpose is to expose the internal LiteralByteBuffer outside
 * of com.google.protobuf and make it possible to access the underlying byte array directly
 *
 * The motivation for this is simple: reduce memory usage and avoid wasting CPU doing unnecessary
 * copies.
 *
 * UniqueID needs:
 *   * access to the underlying byte[] array (for DB access)
 *   * fast conversion to ByteString (for use in protobuf messages)
 *
 * UniqueID is cached (mostly in DirectoryService, as SOID, and Collector, as OCID) so the memory
 * savings are well worth this slightly unorthodox approach:
 *
 * The change brings the retained heap size per OID from 200 bytes to just 56 bytes.
 *
 * We could save another 4 bytes per UniqueID by reimplementing LiteralByteString without caching
 * the result of the hash function (remember, UniqueID does not use this hash value anyway because
 * by virtue of being a reasonably well-distributed PRN it can derive a hash value by selecting any
 * 32 distinct bits from the byte array) but the extra gain is probably not worth it at this point.
 */
public class LeanByteString extends LiteralByteString
{
    /**
     * Create a ByteString wrapping the given bytearray
     * NB: the input is NOT copied
     *
     * @param bytes bytearray to wrap
     */
    public LeanByteString(byte[] bytes)
    {
        super(bytes);
    }

    /**
     * Create a (shallow, if possible) copy of a given bytestring
     *
     * May have to do a deep copy if the given ByteString is not a LeanByteString
     */
    public LeanByteString(ByteString bs)
    {
        // NOTE: we could theoretically share the underlying array when given a LiteralByteString
        // however as BoundedByteString extends LiteralByteString the added complexity required
        // to get it right is not worth the expected gain of avoiding a few copies
        this(bs instanceof LeanByteString ? ((LeanByteString)bs).bytes : bs.toByteArray());
    }

    /**
     * In situations where you only need read-only access to the underlying byte
     * array, use this method to avoid unnecessary copies
     *
     * IMPORTANT: the burden of ensuring the the returned array is never written to
     * is on the caller. Failure to abide by that limitation leads to undefined behavior
     *
     * @return internal byte array
     */
    public byte[] getInternalByteArray() { return bytes; }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof LeanByteString
                ? Arrays.equals(bytes, ((LeanByteString)o).bytes)
                : super.equals(o);
    }
}
