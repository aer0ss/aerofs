package com.aerofs.base.id;

import com.aerofs.base.ParamFactory;

public class RestObject
{
    private final static RestObject ROOT = new RestObject(null, OID.ROOT);
    private final static RestObject APPDATA = new RestObject(null, null);

    final SID sid;
    final OID oid;

    public SID getSID() { return sid; }
    public OID getOID() { return oid; }

    public RestObject(SID sid, OID oid)
    {
        this.sid = sid;
        this.oid = oid;
    }

    private static final int HEXID_LENGTH = UniqueID.LENGTH * 2;

    @ParamFactory
    public static RestObject fromString(String id)
    {
        if (id.equals("root")) return ROOT;
        if (id.equals("appdata")) return APPDATA;
        // allow the use of the SID alone as a shorthand for the root dir of a store
        if (id.length() == HEXID_LENGTH) {
            return new RestObject(new SID(decode(id, 0, HEXID_LENGTH)), OID.ROOT);
        }
        return new RestObject(new SID(decode(id, 0, HEXID_LENGTH)),
                new OID(decode(id, HEXID_LENGTH, 2 * HEXID_LENGTH)));
    }

    // TODO: use checkArgument directly in the UniqueID/OID/SID/... classes
    private static UniqueID decode(String s, int idx, int len)
    {
        try {
            return new UniqueID(s, idx, len);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid id");
        }
    }

    public boolean isRoot()
    {
        return this == ROOT;
    }

    public boolean isAppData()
    {
        return this == APPDATA;
    }

    public String toString()
    {
        if (isRoot()) return "root";
        if (isAppData()) return "appdata";
        return sid.toString() + oid.toString();
    }

    public String toStringFormal()
    {
        if (isRoot()) return "root";
        if (isAppData()) return "appdata";
        return sid.toStringFormal() + oid.toStringFormal();
    }
}
