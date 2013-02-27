package com.aerofs.base.id;

import com.aerofs.base.ex.ExFormatError;
import com.google.protobuf.ByteString;

/**
 * device id
 */
public class DID extends UniqueID
{
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

    /**
     * TODO (WW) use fromInternal and fromExternal as what UserID does?
     */
    public DID(String str) throws ExFormatError
    {
        super(str);
    }

    public static DID generate()
    {
        return new DID(UniqueID.generate());
    }
}
