package com.aerofs.base.id;

import com.aerofs.base.ParamFactory;
import com.aerofs.ids.*;

import static com.google.common.base.Preconditions.checkArgument;

public class RestObject
{
    private final static RestObject ROOT = new RestObject(null, OID.ROOT);
    private final static RestObject APPDATA = new RestObject(null, null);

    private static final int HEXID_LENGTH = UniqueID.LENGTH * 2;

    final SID sid;
    final OID oid;

    public SID getSID() { return sid; }
    public OID getOID() { return oid; }

    public RestObject(SID sid, OID oid)
    {
        this.sid = sid;
        this.oid = oid;
    }

    public RestObject(SID sid)
    {
        this.sid = sid;
        this.oid = OID.ROOT;
    }

    @ParamFactory
    public static RestObject fromString(String id)
    {
        if (id.equals("root")) return ROOT;
        if (id.equals("appdata")) return APPDATA;
        // allow the use of the SID alone as a shorthand for the root dir of a store
        if (id.length() == HEXID_LENGTH) {
            return new RestObject(new SID(decode(id, 0, HEXID_LENGTH)));
        }
        if (id.contains("@")) {
            try {
                return new RestObject(SID.rootSID(UserID.fromExternal(id)));
            } catch (ExInvalidID e) { checkArgument(false); }
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

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (obj == null || !(obj instanceof RestObject)) return false;
        RestObject other = (RestObject)obj;
        boolean sidsEqual = (this.sid == null && other.getSID() == null)
                || (this.sid != null && this.sid.equals(other.getSID()));
        boolean oidsEqual = (this.oid == null && other.getOID() == null)
                || (this.oid != null && this.oid.equals(other.getOID()));
        return sidsEqual && oidsEqual;
    }

    @Override
    public int hashCode()
    {
        int sidHash = this.sid == null ? 0 : this.sid.hashCode();
        int oidHash = this.oid == null ? 0 : this.oid.hashCode();

        // mirrors the implementation of Apache commons' HashCodeBuilder
        return 37 * sidHash + oidHash + 17;
    }
}
