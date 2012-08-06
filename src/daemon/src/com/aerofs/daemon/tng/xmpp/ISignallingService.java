/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp;

import com.aerofs.lib.id.DID;
import com.google.common.base.Predicate;
import com.google.common.util.concurrent.ListenableFuture;

import static com.aerofs.proto.Transport.PBTPHeader;

/**
 * Implemented by classes that provide an out-of-band control, or signalling, service via which
 * messages can be sent to a peer. There are two parties in this relationship: <ol> <li>Signalling
 * service (i.e. the implementer of this interface)</li> <li>Signalling client (i.e. {@link
 * ISignallingClient}</li> </ol>
 * <p/>
 * Implementers provide the following services to clients: <ol> <li>The ability to register
 * themselves to receive signalling messages from a peer</li> <li>The ability to send messages over
 * the signalling channel to a peer</li> </ol>
 * <p/>
 * Clients register themselves with the {@link ISignallingService} and provide a {@link Predicate}
 * which allows the service to query whether a particular client is interested in a message. If a
 * client's Predicate returns true, the client will receive the message for processing.
 */
public interface ISignallingService
{
    /**
     * Register to receive signalling messages that pass the given predicate.
     *
     * @param signallingClient {@link ISignallingClient} to which a matching message should be
     * delivered
     * @param predicate {@link Predicate} that determines whether or not a message matches and
     * should be delivered to the signallingClient
     */
    public void registerSignallingClient_(ISignallingClient signallingClient,
            Predicate<SignallingMessage> predicate);

    /**
     * Deregisters the signallingClient from any further messages received via this service.
     *
     * @param signallingClient The {@link ISignallingClient} to deregister
     */
    public void deregisterSignallingClient_(ISignallingClient signallingClient);

    /**
     * Send a message to a peer via the signalling service.
     *
     * @param message the SignallingMessage to send
     */
    public ListenableFuture<Void> sendSignallingMessage_(SignallingMessage message);

    /**
     * Represents the message sent or received via the signalling service. The did field represents
     * the remote peer at all times, whether sending to the remote peer, or receiving from the
     * remote peer.
     */
    public static final class SignallingMessage
    {
        public final DID did;
        public final PBTPHeader message;

        public SignallingMessage(DID did, PBTPHeader message)
        {
            this.did = did;
            this.message = message;
        }

        public String toString()
        {
            StringBuilder builder = new StringBuilder();
            builder.append("DID: ")
                    .append(did)
                    .append("\n")
                    .append("Type: ")
                    .append(message.getType())
                    .append("\n");
            if (message.hasZephyrInfo()) {
                builder.append("ZephyrInfo:\n");
                builder.append("\tLOCAL_ZID: ");
                if (message.getZephyrInfo().hasDestinationZephyrId()) {
                    builder.append(message.getZephyrInfo().getDestinationZephyrId()).append("\n");
                } else {
                    builder.append("-1\n");
                }
                builder.append("\tREMOTE_ZID: ")
                        .append(message.getZephyrInfo().getSourceZephyrId())
                        .append("\n");
            }
            return builder.toString();
        }
    }
}
