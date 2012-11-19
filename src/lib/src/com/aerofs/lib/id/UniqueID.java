package com.aerofs.lib.id;

import java.util.Arrays;
import java.util.UUID;

import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.IBFKey;
import com.aerofs.lib.ex.ExFormatError;
import com.google.protobuf.ByteString;

// globally unique ids.
//
// templatizing this class is not that easy because of static member ZERO here
//
public class UniqueID implements Comparable<UniqueID>, IBFKey
{
    public static final int LENGTH = 16;

    public static final UniqueID ZERO = new UniqueID(new byte[LENGTH]);

    private final byte[] _bs;

    private Integer _hash;
    private ByteString _bstr;
    private String _str;

    public static UniqueID generate()
    {
        UUID uuid = UUID.randomUUID();

        long v = uuid.getLeastSignificantBits();
        byte [] bs = new byte[LENGTH];
        for(int i = 0; i < 8; i++){
            bs[LENGTH - 1 - i] = (byte)(v >>> (i * 8));
        }

        v = uuid.getMostSignificantBits();
        for(int i = 0; i < 8; i++){
            bs[LENGTH - 8 - 1 - i] = (byte)(v >>> (i * 8));
        }

        return new UniqueID(bs);
    }

    public UniqueID(String str) throws ExFormatError
    {
        this(fromString(str));
    }

    public UniqueID(String str, int start, int end) throws ExFormatError
    {
        this(fromString(str, start, end));
    }

    public UniqueID(byte[] bs)
    {
        assert bs.length == LENGTH;
        _bs = bs;
    }

    public UniqueID(ByteString bstr)
    {
        this(bstr.toByteArray());
        _bstr = bstr;
    }

    protected UniqueID(UniqueID id)
    {
        this(id.getBytes());
    }

    @Override
    public String toString()
    {
        if (_str == null) {
            StringBuilder sb = new StringBuilder();

            sb.append('<');
            for (int i = 0; i < 3; i++) {
                sb.append(String.format("%1$02x", _bs[i]));
            }
            sb.append('>');

            _str = sb.toString();
        }

        return _str;
    }

    public String toStringFormal()
    {
        return Util.hexEncode(_bs);
    }

    public static byte[] fromString(String str) throws ExFormatError
    {
        return fromString(str, 0, str.length());
    }

    public static byte[] fromString(String str, int start, int end) throws ExFormatError
    {
        byte[] bs = Util.hexDecode(str, start, end);
        if (bs.length != LENGTH) throw new ExFormatError("wrong length");
        return bs;
    }

    public static byte[] fromStringFatalOnError(String str)
    {
        try {
            return fromString(str);
        } catch (ExFormatError e) {
            SystemUtil.fatal(e);
            return null;
        }
    }

    @Override
    public byte[] getBytes()
    {
        return _bs;
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && Arrays.equals(_bs, ((UniqueID) o)._bs));
    }

    // hashCode and compareTo start backwards from the last byte of the byte array,
    // as DID names at the beginning.
    @Override
    public int hashCode()
    {
        if (_hash == null) _hash = Arrays.hashCode(_bs);
        return _hash;
    }

    @Override
    public int compareTo(UniqueID id)
    {
        byte [] bs0 = getBytes();
        byte [] bs1 = id.getBytes();
        assert bs0.length == LENGTH && bs1.length == LENGTH;

        for (int i = LENGTH - 1; i >= 0; i--) {
            int diff = bs0[i] - bs1[i];
            if (diff != 0) return diff;
        }
        return 0;
    }

    public ByteString toPB()
    {
        if (_bstr == null) _bstr = ByteString.copyFrom(_bs);
        return _bstr;
    }
}
