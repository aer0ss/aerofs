/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.ids.DID;

public interface IIncomingStreamChunkListener
{
    void onChunkReceived_(DID did, StreamID streamID);
    void onChunkProcessed_(DID did, StreamID streamID);
    void onStreamInvalidated_(DID did, StreamID streamID);
}
