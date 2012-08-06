package com.aerofs.lib.id;

import com.aerofs.lib.SecUtil;
import com.google.protobuf.ByteString;

/**
 * store id
 */
public class SID extends UniqueID
{
    private String _str;

    public SID(ByteString bstr)
    {
        super(bstr);
    }

    public SID(UniqueID id)
    {
        super(id);

        // should use one of the conversion methods below to convert between
        // OID and SID
        assert !(id instanceof OID);
    }

    public SID(byte[] bs)
    {
        super(bs);
    }

    /**
     * Translate an arbitrary folder's OID into the SID of the store to which
     * this folder converts.
     */
    public static SID folderOID2convertedStoreSID(OID oid)
    {
        return new SID(SecUtil.newMessageDigestMD5().digest(oid.getBytes()));
    }

    /**
     * Translate a store SID to the OID of the anchor that holds the store
     */
    public static OID storeSID2anchorOID(SID sid)
    {
        return new OID(sid.getBytes());
    }

    /**
     * Translate the OID of an anchor to the SID of the store that the anchor
     * holds
     */
    public static SID anchorOID2storeSID(OID oid)
    {
        return new SID(oid.getBytes());
    }

    @Override
    public String toString()
    {
        if (_str == null) {
            StringBuilder sb = new StringBuilder();

            sb.append('$');
            for (int i = 0; i < 2; i++) {
                sb.append(String.format("%1$02x", getBytes()[i]));
            }
            sb.append('$');

            _str = sb.toString();
        }

        return _str;
    }
}
