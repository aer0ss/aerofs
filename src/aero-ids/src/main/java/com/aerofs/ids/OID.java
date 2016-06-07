package com.aerofs.ids;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

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

    public OID(UniqueID id)
    {
        super(id);
        assertIsValid();

        // should use one of the conversion methods in Identifiers.java to convert between
        // OID and SID
        checkArgument(!(id instanceof SID));
    }

    public OID(byte[] bs)
    {
        super(bs);
        assertIsValid();
    }

    public OID(String str) throws ExInvalidID
    {
        super(str);
        assertIsValid();
    }

    private void assertIsValid()
    {
        int v = getVersionNibble();
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
        return !isRoot() && !isTrash() && getVersionNibble() == 0;
    }

    public static OID generate()
    {
        return new OID(UniqueID.generate());
    }
}
