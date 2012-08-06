/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.event.net.tng.rx;

import com.aerofs.daemon.event.net.tng.Endpoint;
import com.aerofs.daemon.tng.IIncomingStream;

import java.io.ByteArrayInputStream;

/* steps to receive chunked messages:
 *
 *  on dequeuing EIStreamBegun:
 *  do {
 *      dequeue EIChunk's
 *  } while (EIStreamAborted is not received)
 *
 *  enqueue EORxEndStream to release transport resources, or IEORxAbortStream
 *  to notify the sender to abort.
 */

public class EIStreamBegun extends EIChunk
{
    /**
     * Constructor
     * <p/>
     * For parameter information, {@see EIChunk.EIChunk}
     */
    public EIStreamBegun(Endpoint ep, IIncomingStream stream, ByteArrayInputStream is, int wirelen)
    {
        super(ep, stream, 0, is, wirelen);
    }
}

