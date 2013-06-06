/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.id.DID;

import static com.aerofs.proto.Transport.PBTPHeader;

/**
 * Implemented by classes that provide an out-of-band control, or signalling,
 * channel via which messages can be sent to a peer. There are two parties in this
 * relationship:
 * <ol>
 *     <li>Signalling channel provider (i.e. the implementer of this interface)</li>
 *     <li>Signalling channel client (i.e. {@link ISignallingClient}</li>
 * </ol>
 * Implementers provide the following services to clients:
 * <ol>
 *     <li>The ability to register interest in different types of messages that
 *         may be sent over the signalling channel</li>
 *     <li>The ability to send messages over the signalling channel to a peer</li>
 * </ol>
 */
public interface ISignallingService
{
    /**
     * Register to receive messages of a specified {@link PBTPHeader.Type} that
     * may be sent over the signalling channel
     *
     * @param client {@link com.aerofs.daemon.transport.xmpp.ISignallingClient} to which a matching message should
     * be delivered
     */
    public void registerSignallingClient(ISignallingClient client);

    /**
     * Send a message to a peer via the signalling channel
     *
     * @param did {@link com.aerofs.base.id.DID} of the peer to sent the message to
     * @param msg {@link com.aerofs.proto.Transport.PBTPHeader} that forms the message payload. Implementers
     * of <code>ISignallingService</code>are free to transform the message as
     * necessary before it is sent out over the signalling channel.
     * @param client {@link com.aerofs.daemon.transport.xmpp.ISignallingClient} to be notified if message sending fails.
 * Implementers of <code>ISignallingClient</code> <strong>MUST</strong> call
 * this parameter's <code>sendSignallingMessageFailed</code> method if the
     */
    public void sendSignallingMessage(DID did, byte[] msg, ISignallingClient client);
}
