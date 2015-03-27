package com.aerofs.daemon.event.net.tx;

import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.ids.DID;
import com.aerofs.lib.event.IEvent;

public class EOTxEndStream implements IEvent
{
    public final DID _did;
    public final StreamID _streamId;

    public EOTxEndStream(DID did, StreamID streamId)
    {
        _did = did;
        _streamId = streamId;
    }

    @Override
    public String toString()
    {
        return "txEndStrm " + _streamId;
    }
}
