package com.aerofs.daemon.event.net.rx;

import com.aerofs.ids.UserID;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;

import java.io.InputStream;

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
     *
     * For parameter information, {@see EIChunk.EIChunk}
     */
    public EIStreamBegun(Endpoint ep, UserID userID, StreamID strid, InputStream is)
    {
        super(ep, userID, strid, 0, is);
    }
}

