package com.aerofs.daemon.event.net.tx;

import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.ids.DID;
import com.aerofs.lib.event.IEvent;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;

// similar to chunks, the transport shall guarantee delivery of the abortion to
// the receiver
//
public class EOTxAbortStream implements IEvent
{
    public final DID _did;
    public final StreamID _streamId;
    public final InvalidationReason _reason;

    public EOTxAbortStream(DID did, StreamID streamId, InvalidationReason reason)
    {
        _did = did;
        _streamId = streamId;
        _reason = reason;
    }

    @Override
    public String toString()
    {
        return "abort outgoing stream:" + _streamId + " " + _reason;
    }
}
