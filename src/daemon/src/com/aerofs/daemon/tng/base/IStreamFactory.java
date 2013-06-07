/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IIncomingStream;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.ex.ExStreamAlreadyExists;

public interface IStreamFactory
{
    IOutgoingStream createOutgoing_(IConnection connection, StreamID id, Prio pri)
            throws ExStreamAlreadyExists;

    IIncomingStream createIncoming_(IConnection connection, StreamID id, Prio pri)
            throws ExStreamAlreadyExists;
}