/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.ex;

import com.aerofs.daemon.lib.id.StreamID;

public class ExStreamAlreadyExists extends ExTransport
{
    private static final long serialVersionUID = 1L;

    private final StreamID _id;

    public ExStreamAlreadyExists(StreamID id)
    {
        super("duplicate stream:" + id.getInt());
        this._id = id;
    }

    public StreamID getStreamId_()
    {
        return _id;
    }
}
