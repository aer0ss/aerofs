package com.aerofs.daemon.event.net.tx;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.event.IEvent;

public class EOUnicastMessage implements IEvent, IOutputBuffer
{
    public final DID _to;
    private final byte[] _bs;
    public final SID _sid;

    public EOUnicastMessage(DID to, SID sid, byte[] bs)
    {
        _to = to;
        _bs = bs;
        _sid = sid;
    }

    @Override
    public byte[] byteArray()
    {
        return _bs;
    }
}
