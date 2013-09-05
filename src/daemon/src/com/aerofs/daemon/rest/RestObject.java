package com.aerofs.daemon.rest;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UniqueID.ExInvalidID;
import com.aerofs.base.id.UserID;

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

    private static final String ROOT_TOKEN = "@root";

    public static RestObject fromStringFormal(String id, UserID userid)
            throws ExInvalidID, ExFormatError
    {
        SID sid;
        int base;

        if (id.startsWith(ROOT_TOKEN)) {
            sid = SID.rootSID(userid);
            base = ROOT_TOKEN.length();
        } else {
            sid = new SID(id, 0, UniqueID.LENGTH *2);
            base = UniqueID.LENGTH * 2;
        }

        OID oid;
        if (id.length() == base + UniqueID.LENGTH * 2) {
            oid = new OID(new UniqueID(id, base, base + UniqueID.LENGTH * 2));
        } else if (id.length() == base) {
            // no OID provided -> default to ROOT
            oid = OID.ROOT;
        } else {
            throw new ExInvalidID();
        }

        return new RestObject(sid, oid);
    }
}
