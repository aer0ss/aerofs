/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.streams;

import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.base.async.UncancellableFuture;

public final class NewOutgoingStream
{
    private final StreamID _id;
    private final UncancellableFuture<IOutgoingStream> _streamCreationFuture = UncancellableFuture.create();

    public NewOutgoingStream(StreamID id)
    {
        this._id = id;
    }

    public StreamID getId_()
    {
        return _id;
    }

    public UncancellableFuture<IOutgoingStream> getStreamCreationFuture_()
    {
        return _streamCreationFuture;
    }
}