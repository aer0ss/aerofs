package com.aerofs.daemon.core.net;

import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;

/**
 * Interface to be implemented by a layer that processes incoming messages from
 * an AeroFS device
 *
 */
public interface IUnicastInputLayer
{
    /**
     * Called when an atomic message is received from a peer
     *
     * @param r {@link RawMessage} which contains the serialized message
     * @param pc {@link PeerContext} with parameters about the endpoint
     * that sent this message
     */
    void onUnicastDatagramReceived_(RawMessage r, PeerContext pc);

    /**
     * Called when the head of a stream is received from a peer
     *
     * @param streamId Identifies the new stream that this head begins
     * @param r <code>RawMessage</code> which contains the serialized stream head
     * @param pc <code>PeerContext</code> with parameters about the endpoint
     * that sent this stream head
     */
    void onStreamBegun_(StreamID streamId, RawMessage r, PeerContext pc);

    /**
     * Called for <i>each</i> stream chunk received from a peer
     *
     * @param streamId Identifies the stream to which the stream chunk belongs
     * @param seq Sequence number of the stream chunk. Stream chunks are
     * delivered in strictly monotonically increasing order
     * @param r <code>RawMessage</code> which contains the serialized stream head
     * @param pc <code>PeerContext</code> with parameters about the endpoint
     * that sent this stream head
     */
    void onStreamChunkReceived_(StreamID streamId, int seq, RawMessage r, PeerContext pc);

    /**
     * Called when the stream is aborted for whatever reason
     *
     * @param streamId Identifies the stream that was aborted
     * @param ep {@link Endpoint}
     * @param reason
     */
    void onStreamAborted_(StreamID streamId, Endpoint ep, InvalidationReason reason);
}
