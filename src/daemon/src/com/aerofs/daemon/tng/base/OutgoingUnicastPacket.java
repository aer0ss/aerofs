/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

public final class OutgoingUnicastPacket
{
    private final byte[] _payload;

    public OutgoingUnicastPacket(byte[] payload)
    {
        this._payload = payload;
    }

    public byte[] getPayload_()
    {
        return _payload;
    }
}