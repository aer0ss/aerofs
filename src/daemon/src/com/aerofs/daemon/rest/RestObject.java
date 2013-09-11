package com.aerofs.daemon.rest;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UniqueID.ExInvalidID;

public class RestObject
{
    public final SID sid;
    public final OID oid;

    public RestObject(SID sid, OID oid)
    {
        this.sid = sid;
        this.oid = oid;
    }

    public String toString()
    {
        return sid.toString() + oid.toString();
    }

    public String toStringFormal()
    {
        return sid.toStringFormal() + oid.toStringFormal();
    }

    private static final int HEXID_LENGTH = UniqueID.LENGTH * 2;

    public static RestObject fromStringFormal(String id)
            throws ExInvalidID, ExFormatError
    {
        SID sid = new SID(id, 0, HEXID_LENGTH);
        OID oid = new OID(new UniqueID(id, HEXID_LENGTH, 2 * HEXID_LENGTH));
        return new RestObject(sid, oid);
    }
}
