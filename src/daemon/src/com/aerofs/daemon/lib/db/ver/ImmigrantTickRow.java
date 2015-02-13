package com.aerofs.daemon.lib.db.ver;

import com.aerofs.ids.DID;
import com.aerofs.lib.Tick;
import com.aerofs.lib.id.CID;
import com.aerofs.ids.OID;

public class ImmigrantTickRow extends AbstractTickRow
{
    public final DID _did;
    public final Tick _immTick;
    // the value of the Immigrant DID field is implicit as the row's key

    public ImmigrantTickRow(OID oid, CID cid, DID did, Tick tick, Tick immTick)
    {
        super(oid, cid, tick);
        _did = did;
        _immTick = immTick;
    }
}
