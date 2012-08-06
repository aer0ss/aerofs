/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;

import java.io.ByteArrayInputStream;

public final class IncomingAeroFSPacket // FIXME: merge with OutgoingAeroFSPacket
{
    private final PBTPHeader _hdr;
    private final ByteArrayInputStream _is; // [sigh] unfortunately, this is mutable
    private final int _wirelen;

    public IncomingAeroFSPacket(PBTPHeader hdr, ByteArrayInputStream is, int wirelen)
    {
        _hdr = hdr;
        _is = is;
        _wirelen = wirelen;
    }

    public Type getType_()
    {
        return getHeader_().getType();
    }

    public PBTPHeader getHeader_()
    {
        return _hdr;
    }

    public ByteArrayInputStream getPayload_()
    {
        return _is;
    }

    public int getWirelen_()
    {
        return _wirelen;
    }
}
