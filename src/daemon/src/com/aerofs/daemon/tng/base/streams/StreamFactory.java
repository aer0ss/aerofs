/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.streams;

import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IIncomingStream;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.tng.base.IStreamFactory;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.ex.ExStreamAlreadyExists;
import com.aerofs.base.id.DID;

public final class StreamFactory implements IStreamFactory
{
    private final ISingleThreadedPrioritizedExecutor _streamExecutor;
    private final DID _did;

    public StreamFactory(ISingleThreadedPrioritizedExecutor streamExecutor, DID did)
    {
        this._streamExecutor = streamExecutor;
        this._did = did;
    }

    @Override
    public IOutgoingStream createOutgoing_(IConnection connection, StreamID id, Prio pri)
            throws ExStreamAlreadyExists
    {
        return OutgoingStream.getInstance_(_streamExecutor, connection, id, _did, pri);
    }

    @Override
    public IIncomingStream createIncoming_(IConnection connection, StreamID id, Prio pri)
            throws ExStreamAlreadyExists
    {
        return IncomingStream.getInstance_(_streamExecutor, connection, id, _did, pri);
    }
}