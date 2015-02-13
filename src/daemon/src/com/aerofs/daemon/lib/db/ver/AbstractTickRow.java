package com.aerofs.daemon.lib.db.ver;

import com.aerofs.lib.Tick;
import com.aerofs.lib.id.CID;
import com.aerofs.ids.OID;

public abstract class AbstractTickRow
{
    public final OID _oid;
    public final CID _cid;
    public final Tick _tick;

    protected AbstractTickRow(OID oid, CID cid, Tick tick)
    {
        _oid = oid;
        _cid = cid;
        _tick = tick;
    }
}
