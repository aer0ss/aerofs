/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IIncomingStream;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.ex.ExStreamAlreadyExists;
import com.aerofs.base.id.SID;

public interface IStreamFactory
{
    IOutgoingStream createOutgoing_(IConnection connection, StreamID id, SID sid, Prio pri)
            throws ExStreamAlreadyExists;

    IIncomingStream createIncoming_(IConnection connection, StreamID id, SID sid, Prio pri)
            throws ExStreamAlreadyExists;
}