package com.aerofs.daemon.event.net.tx;

import com.aerofs.base.id.DID;
import com.aerofs.lib.event.IEvent;

public class EOUnicastMessage implements IEvent, IOutputBuffer
{
    public final DID _to;
    private final byte[] _bs;

    public EOUnicastMessage(DID to, byte[] bs)
    {
        _to = to;
        _bs = bs;
    }

    @Override
    public byte[] byteArray()
    {
        return _bs;
    }
}
