/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport;

import com.aerofs.ids.DID;


/**
 * Implemented by classes that want to send and receive messages via an
 * out-of-band signalling (or control) channel. This interface is the dual of
 * {@link ISignallingService} and represents the <em>client</em> of the
 * <code>ISignallingService</code> provider.
 */
public interface ISignallingServiceListener
{
    /**
     * Called when a connection is established to the out-of-band signalling
     * channel
     */
    public void signallingServiceConnected();

    /**
     * Called when the connection to the out-of-band signalling channel is broken.
     * The signalling channel is unusable until the client is signalled via
     * <code>signallingServiceConnected</code> that the connection has been
     * re-established.
     */
    public void signallingServiceDisconnected();

    /**
     * Called when a message of the type the client registered for via
     * <code>registerSignallingClient()</code> in {@link ISignallingService}
     * is received on the signalling channel
     *
     * @param did {@link DID} of the peer that sent this message
     * @param message {@link com.aerofs.proto.Transport.PBTPHeader} body of the message received on the signalling channel
     */
    public void processIncomingSignallingMessage(DID did, byte[] message);

    /**
     * Called when a message that the client wanted to send on the signalling
     * channel via <code>sendSignallingMessage()</code> cannot be sent
     * because of an error. This is an <em>error callback method</em>.
     *
     *
     * @param did {@link com.aerofs.base.id.DID} of the peer to which the message was supposed to be sent
     * @param failedMessage {@link com.aerofs.proto.Transport.PBTPHeader} message (in original, client-supplied
     * format) that could not be sent via the signalling channel
     * @param cause Exception that prevented the message from being sent
     */
    public void sendSignallingMessageFailed(DID did, byte[] failedMessage, Exception cause);
}
