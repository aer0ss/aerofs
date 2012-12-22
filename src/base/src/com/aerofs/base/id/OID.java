package com.aerofs.base.id;

import java.util.Arrays;

import com.aerofs.base.ex.ExFormatError;
import com.google.protobuf.ByteString;

/**
 * object id
 */
public class OID extends UniqueID
{

    public static final OID ROOT;   // all zeros
    public static final OID TRASH;  // all zeros except one bit

    static {
        byte[] bs = UniqueID.ZERO.getBytes();
        bs = Arrays.copyOf(bs, bs.length);
        bs[0] = 1;
        TRASH = new OID(bs);
        ROOT = new OID(UniqueID.ZERO);
    }

    public OID(ByteString bstr)
    {
        super(bstr);
        assertIsValid();
    }

    public OID(UniqueID id)
    {
        super(id);
        assertIsValid();

        // should use one of the conversion methods below to convert between
        // OID and SID
        assert !(id instanceof SID);
    }

    public OID(byte[] bs)
    {
        super(bs);
        assertIsValid();
    }

    public OID(String str) throws ExFormatError
    {
        super(str);
        assertIsValid();
    }

    private void assertIsValid()
    {
        int v = getVersionNibble(getBytes());
        assert v == 0 || v == 4 : toStringFormal();
    }

    public boolean isRoot()
    {
        return equals(ROOT);
    }

    public boolean isTrash()
    {
        return equals(TRASH);
    }

    public boolean isAnchor()
    {
        /**
         * Using 0 for anchors because it is not allocated by RFC 4122, 4 for non-anchors to limit
         * DB changes to anchor OIDs (hence the need to explicitly distinguish anchors from root and
         * trash)
         */
        return !isRoot() && !isTrash() && getVersionNibble(getBytes()) == 0;
    }

    /**
     * DO NOT USE outside of one-time-use migration code where bypassing structural constraints
     * is needed to convert old data to valid new data.
     */
    public static OID legacyValue(byte[] bs)
    {
        byte b = bs[VERSION_BYTE];
        bs[VERSION_BYTE] = 0;
        OID oid = new OID(bs);
        oid.getBytes()[VERSION_BYTE] = b;
        return oid;
    }
}
