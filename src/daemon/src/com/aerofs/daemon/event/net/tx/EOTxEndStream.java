package com.aerofs.daemon.event.net.tx;

import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.event.IEvent;

public class EOTxEndStream implements IEvent
{
    public final StreamID _streamId;

    public EOTxEndStream(StreamID streamId)
    {
        _streamId = streamId;
    }

    @Override
    public String toString()
    {
        return "txEndStrm " + _streamId;
    }
}
