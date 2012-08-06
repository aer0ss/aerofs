package com.aerofs.daemon.event.net.rx;

import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.net.Endpoint;

// N.B. this message must be delivered successfully. i.e. you can't drop the
// message due to flow control.
//
// It's safe to issue this event multiple times for the same end point. The core
// ignores this event for unknown end points.
//
public class EISessionEnded implements IEvent {

    private final Endpoint _ep;
    private final boolean _out;
    private final boolean _in;

    public EISessionEnded(Endpoint ep, boolean outbound, boolean inbound)
    {
        _ep = ep;
        _out = outbound;
        _in = inbound;
    }

    public Endpoint ep()
    {
        return _ep;
    }

    // at least one of the following methods must return true
    public boolean outbound()
    {
        return _out;
    }

    public boolean inbound()
    {
        return _in;
    }

}
