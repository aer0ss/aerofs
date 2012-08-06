/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp;

import com.aerofs.daemon.tng.xmpp.ISignallingService.SignallingMessage;
import com.aerofs.lib.id.DID;
import com.aerofs.proto.Transport.PBTPHeader;

/**
 * Implemented by classes that want to send and receive messages via an out-of-band signalling (or
 * control) channel. This interface is the dual of {@link ISignallingService} and represents the
 * <em>client</em> of the <code>ISignallingService</code> provider.
 */
public interface ISignallingClient
{
    /**
     * Called when a connection is established to the out-of-band signalling channel
     */
    public void signallingChannelConnected_();

    /**
     * Called when the connection to the out-of-band signalling channel is broken. The signalling
     * channel is unusable until the client is signalled via <code>signallingChannelConnected_</code>
     * that the connection has been re-established.
     */
    public void signallingChannelDisconnected_();

    /**
     * Called when a message of the type the client registered for via
     * <code>registerSignallingClient_()</code> in {@link ISignallingService} is received on the
     * signalling channel. If the client does not want to handle the given message, the client
     * should return false from this method so that other ISignallingClient's can have a chance to
     * process the message
     *
     * @param did {@link DID} of the peer that sent this message
     * @param msg {@link PBTPHeader} body of the message received on the signalling channel
     */
    public void processSignallingMessage_(SignallingMessage message);
}
