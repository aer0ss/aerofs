/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.ex;

import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.proto.Transport;

public class ExStreamInvalid extends ExTransport
{
    private static final long serialVersionUID = 1L;

    private final Transport.PBStream.InvalidationReason _reason;
    private final StreamID _id;

    public ExStreamInvalid(StreamID id, Transport.PBStream.InvalidationReason reason)
    {
        super("id:" + id + ", reason:" + reason);
        this._id = id;
        this._reason = reason;
    }

    public StreamID getStreamId_()
    {
        return _id;
    }

    public Transport.PBStream.InvalidationReason getReason_()
    {
        return _reason;
    }
}
