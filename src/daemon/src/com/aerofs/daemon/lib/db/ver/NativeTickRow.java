package com.aerofs.daemon.lib.db.ver;

import com.aerofs.lib.Tick;
import com.aerofs.lib.id.CID;
import com.aerofs.ids.OID;

public class NativeTickRow extends AbstractTickRow
{
    // the value of the DID field is implicit as the row's key

    public NativeTickRow(OID oid, CID cid, Tick tick)
    {
        super(oid, cid, tick);
    }
}
