package com.aerofs.daemon.rest.util;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.google.common.base.Preconditions;

public class RestObject
{
    public final SID sid;
    public final OID oid;

    public RestObject(SID sid, OID oid)
    {
        this.sid = sid;
        this.oid = oid;
    }

    private static final int HEXID_LENGTH = UniqueID.LENGTH * 2;
    public RestObject(String id)
    {
        this(new SID(decode(id, 0, HEXID_LENGTH)),
                new OID(decode(id, HEXID_LENGTH, 2 * HEXID_LENGTH)));
    }

    // TODO: use checkArgument directly in the UniqueID/OID/SID/... classes
    private static UniqueID decode(String s, int idx, int len)
    {
        try {
            return new UniqueID(s, idx, len);
        } catch (Exception e) {
            Preconditions.checkArgument(false);
            throw new IllegalArgumentException(e);
        }
    }

    public String toString()
    {
        return sid.toString() + oid.toString();
    }

    public String toStringFormal()
    {
        return sid.toStringFormal() + oid.toStringFormal();
    }
}
