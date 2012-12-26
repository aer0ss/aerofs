/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.streams;

import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.id.SID;

public final class NewOutgoingStream
{
    private final StreamID _id;
    private final SID _sid;
    private final UncancellableFuture<IOutgoingStream> _streamCreationFuture = UncancellableFuture.create();

    public NewOutgoingStream(StreamID id, SID sid)
    {
        this._id = id;
        this._sid = sid;
    }

    public StreamID getId_()
    {
        return _id;
    }

    public SID getSid_()
    {
        return _sid;
    }

    public UncancellableFuture<IOutgoingStream> getStreamCreationFuture_()
    {
        return _streamCreationFuture;
    }
}