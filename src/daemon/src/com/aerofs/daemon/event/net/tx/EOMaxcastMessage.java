package com.aerofs.daemon.event.net.tx;

import com.aerofs.ids.SID;
import com.aerofs.lib.event.IEvent;

public class EOMaxcastMessage implements IEvent, IOutputBuffer {

    public final SID _sid;
    public final int _mcastid;
    private final byte[] _bs;

    public EOMaxcastMessage(SID sid, int mcastid, byte[] bs)
    {
        _sid = sid;
        _mcastid = mcastid;
        _bs = bs;
    }

    @Override
    public byte[] byteArray()
    {
        return _bs;
    }

}
