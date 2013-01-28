/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.event.net.tng.rx;

import com.aerofs.daemon.event.net.tng.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.event.IEvent;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;

public class EIStreamAborted implements IEvent
{
    private final Endpoint _ep;
    private final StreamID _strm;
    private final InvalidationReason _reason;

    public EIStreamAborted(Endpoint ep, StreamID sid, InvalidationReason reason)
    {
        _ep = ep;
        _strm = sid;
        _reason = reason;
    }

    public StreamID sid()
    {
        return _strm;
    }

    public Endpoint ep()
    {
        return _ep;
    }

    public InvalidationReason reason()
    {
        return _reason;
    }
}
