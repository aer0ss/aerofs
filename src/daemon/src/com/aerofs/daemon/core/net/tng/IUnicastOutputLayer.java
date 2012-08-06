/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.tng;

import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;

public interface IUnicastOutputLayer
{
    void sendDatagram_(byte[] bs, PeerContext pc)
        throws Exception;

    void beginOutgoingStream_(StreamID streamId, byte[] bs,
            PeerContext pc, Token tk)
        throws Exception;

    /**
     * Theoretically, we needn't pass pc to sendOutgoingStreamChunk_, endOutgoingStream_ or
     * abortOutgoingStream_ as it can be uniquely identified by streamId. In practice, we still
     * pass the info so that layer implementation may be stateless
     *
     * @seq the sequence number of the current chunk, starting at 1 (chunk 0
     * is the one in beginOutgoingStream_()). This number is not needed in theory,
     * because lower layers should guarantee the order of the chunks. however,
     * no one can guarantee lower layers are bug free, and corrupting user data
     * is catastrophic. so we'd keep it.
     *
     */
    void sendOutgoingStreamChunk_(StreamID streamId, int seq, byte[] bs,
            PeerContext pc, Token tk)
        throws Exception;

    void endOutgoingStream_(StreamID streamId, PeerContext pc)
        throws ExNoResource, ExAborted;

    void abortOutgoingStream_(StreamID streamId, InvalidationReason reason,
            PeerContext pc)
        throws ExNoResource, ExAborted;

    /**
     * This function is called from within the <i>IUnicastInputLayer</i> stack.
     *
     * This is because we may have an error while processing <i>incoming</i>
     * messages and have to abort receiving. Unfortunately right now the layer
     * stacks are singly linked lists: Input (i.e. receiving) transport -> core;
     * Output: (i.e. sending) core -> transport. But when you have an error you
     * have to notify the layer stack that <i>sent you the message</i>. Since
     * only this interface handles messages/feedback going to the transport, this
     * function is here.
     *
     * <b>IMPORTANT:</b> FIXME!!! This should be refactored into a separate interface!
     */
    void endIncomingStream_(StreamID streamId, PeerContext pc)
        throws ExNoResource, ExAborted;
}
