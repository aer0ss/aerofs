package com.aerofs.base.id;

import com.aerofs.base.ex.ExFormatError;
import com.google.protobuf.ByteString;

/**
 * device id
 */
// TODO (MP) remove public constructors use fromExternal() fromInternal().
public class DID extends UniqueID
{
    protected static final int MDID_VERSION_NIBBLE = 8;

    public DID(ByteString bstr)
    {
        super(bstr);
    }

    public DID(UniqueID id)
    {
        super(id);
    }

    public DID(byte[] bs)
    {
        super(bs);
    }

    public DID(String str, int start, int end) throws ExFormatError
    {
        super(str, start, end);
    }

    public DID(String str) throws ExFormatError
    {
        super(str);
    }

    public static DID generate()
    {
        return new DID(UniqueID.generate());
    }

    public static DID fromExternal(byte[] bs) throws ExFormatError
    {
        if (bs.length != UniqueID.LENGTH) {
            throw new ExFormatError();
        }

        return new DID(bs);
    }

    public static DID fromInternal(byte[] bs) throws ExFormatError
    {
        return fromExternal(bs);
    }

    public boolean isMobileDevice()
    {
        return getVersionNibble() == MDID_VERSION_NIBBLE;
    }
}
