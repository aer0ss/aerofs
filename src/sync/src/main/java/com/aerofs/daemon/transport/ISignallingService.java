/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.ids.DID;

/**
 * Implemented by classes that provide an out-of-band control, or signalling,
 * channel via which messages can be sent to a peer. There are two parties in this
 * relationship:
 * <ol>
 *     <li>Signalling channel provider (i.e. the implementer of this interface)</li>
 *     <li>Signalling channel client (i.e. {@link ISignallingServiceListener}</li>
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
     * Register to receive messages of a specified {@link com.aerofs.proto.Transport.PBTPHeader.Type} that
     * may be sent over the signalling channel
     *
     * @param client {@link ISignallingServiceListener} to which a matching message should
     * be delivered
     */
    public void registerSignallingClient(ISignallingServiceListener client);

    /**
     * Send a message to a peer via the signalling channel
     *
     * @param did {@link com.aerofs.ids.DID} of the peer to sent the message to
     * @param msg {@link com.aerofs.proto.Transport.PBTPHeader} that forms the message payload. Implementers
     * of <code>ISignallingService</code>are free to transform the message as
     * necessary before it is sent out over the signalling channel.
     * @param client {@link ISignallingServiceListener} to be notified if message sending fails.
     * Implementers of <code>ISignallingServiceListener</code> <strong>MUST</strong> call
     * this parameter's <code>sendSignallingMessageFailed</code> method if the message could not be
     * sent.
     */
    public void sendSignallingMessage(DID did, byte[] msg, ISignallingServiceListener client);
}
