/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.lib.id.SID;

public final class OutgoingUnicastPacket
{
    private final SID _sid;
    private final byte[] _payload;

    public OutgoingUnicastPacket(SID sid, byte[] payload)
    {
        this._sid = sid;
        this._payload = payload;
    }

    public SID getSid_()
    {
        return _sid;
    }

    public byte[] getPayload_()
    {
        return _payload;
    }
}