package com.aerofs.lib.id;

import java.util.Arrays;

import com.aerofs.lib.ex.ExFormatError;
import com.google.protobuf.ByteString;

/**
 * object id
 */
public class OID extends UniqueID {

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
    }

    public OID(UniqueID id)
    {
        super(id);

        // should use one of the conversion methods below to convert between
        // OID and SID
        assert !(id instanceof SID);
    }

    public OID(byte[] bs)
    {
        super(bs);
    }

    public OID(String str) throws ExFormatError
    {
        super(str);
    }

    public boolean isRoot()
    {
        return equals(ROOT);
    }
}
