package com.aerofs.daemon.event.net.tx;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;

/* steps to send streams:
 *
 *  execute IEOBeginStream
 *  for (...) {
 *      execute IEOChunk
 *  }
 *  enqueueBlocking IEOEndStream    // must ensure the event has been enqueued
 *                                  // otherwise there would be mem leaks in
 *                                  // transport
 *
 *  At any time after IEOBeginStream, enqueueBlocking IEOTxAbortStream to abort.
 *  The receiver will be notified immediately. No IEOChunk or IEOEndStream may
 *  issued after IEOTxAbortStream
 *
 * N.B. streams are always at background priority to allow atomic messages
 * go first.
 */

public class EOBeginStream extends EOChunk
{
    public EOBeginStream(StreamID streamId, DID did, SID sid, byte[] bs, IIMCExecutor imce)
    {
        super(streamId, 0, did, sid, bs, imce);
    }

    @Override
    public String toString()
    {
        return "beginStrm " + _streamId;
    }
}
