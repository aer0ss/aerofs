package com.aerofs.daemon.core.net;

import com.aerofs.daemon.lib.id.StreamID;

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
}
