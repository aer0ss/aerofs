/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import javax.annotation.Nullable;

import static com.aerofs.proto.Transport.PBTPHeader;

public final class OutgoingAeroFSPacket
{
    private final PBTPHeader _hdr;
    @Nullable private final byte[] _payload;

    public OutgoingAeroFSPacket(PBTPHeader hdr, @Nullable byte[] payload)
    {
        this._hdr = hdr;
        this._payload = payload;
    }

    public PBTPHeader getHeader_()
    {
        return _hdr;
    }

    @Nullable
    public byte[] getData_()
    {
        return _payload;
    }
}