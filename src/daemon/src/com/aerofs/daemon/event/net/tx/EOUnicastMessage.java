package com.aerofs.daemon.event.net.tx;

import com.aerofs.ids.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.lib.event.IEvent;

public class EOUnicastMessage implements IEvent, IOutputBuffer
{
    public final DID _to;
    private final byte[] _bs;
    private IResultWaiter _waiter; // optional

    public EOUnicastMessage(DID to, byte[] bs)
    {
        _to = to;
        _bs = bs;
    }

    public void setWaiter(IResultWaiter waiter)
    {
        _waiter = waiter;
    }

    public IResultWaiter getWaiter()
    {
        return _waiter;
    }

    @Override
    public byte[] byteArray()
    {
        return _bs;
    }
}
