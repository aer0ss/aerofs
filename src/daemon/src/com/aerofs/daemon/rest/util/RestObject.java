package com.aerofs.daemon.rest.util;

import com.aerofs.base.ParamFactory;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;

public class RestObject
{
    public final static RestObject ROOT = new RestObject(null, OID.ROOT);
    public final static RestObject APPDATA = new RestObject(null, null);

    final SID sid;
    final OID oid;

    public RestObject(SID sid, OID oid)
    {
        this.sid = sid;
        this.oid = oid;
    }

    private static final int HEXID_LENGTH = UniqueID.LENGTH * 2;
    private RestObject(String id)
    {
        this(new SID(decode(id, 0, HEXID_LENGTH)),
                new OID(decode(id, HEXID_LENGTH, 2 * HEXID_LENGTH)));
    }

    @ParamFactory
    public static RestObject fromString(String id)
    {
        if (id.equals("root")) return ROOT;
        if (id.equals("appdata")) return APPDATA;
        return new RestObject(id);
    }

    // TODO: use checkArgument directly in the UniqueID/OID/SID/... classes
    private static UniqueID decode(String s, int idx, int len)
    {
        try {
            return new UniqueID(s, idx, len);
        } catch (Exception e) {
            throw new IllegalArgumentException();
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
