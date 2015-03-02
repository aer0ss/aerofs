package com.aerofs.daemon.event.net.rx;

import com.aerofs.lib.event.IEvent;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;

public class EIStreamAborted implements IEvent {

    private final Endpoint _ep;
    private final StreamID _streamId;
    private final InvalidationReason _reason;

    public EIStreamAborted(Endpoint ep, StreamID streamId, InvalidationReason reason)
    {
        _ep = ep;
        _streamId = streamId;
        _reason = reason;
    }

    public StreamID sid()
    {
        return _streamId;
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
