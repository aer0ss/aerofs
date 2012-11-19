package com.aerofs.lib.id;

import com.aerofs.lib.ex.ExFormatError;
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

    public DID(String str) throws ExFormatError
    {
        super(str);
    }

    /**
     * @param fatalOnError its value is disregarded
     */
    public DID(String str, boolean fatalOnError)
    {
        this(UniqueID.fromStringFatalOnError(str));
    }
}
