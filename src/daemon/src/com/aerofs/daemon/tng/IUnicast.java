/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.google.common.util.concurrent.ListenableFuture;

// FIXME: add a parameter to indicate if packet must be sent via reliable channels (some messages without stream ids - transport flood, sent by <code>sendUnicastPacket_</code>) need reliability
// IMPORTANT: in general we always need to pass priority along because the entire stack has to be aware of a packet's priority
public interface IUnicast
{
    ListenableFuture<Void> sendDatagram_(DID did, SID sid, byte[] payload, Prio pri);

    ListenableFuture<IOutgoingStream> beginStream_(StreamID id, DID did, SID sid, Prio pri);

    ListenableFuture<Void> pulse_(DID did, Prio pri);
}