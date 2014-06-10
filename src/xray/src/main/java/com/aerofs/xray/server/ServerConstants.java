/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.xray.server;

/**
 * Constants that are relevant to the {@link XRayServer} server only
 */
final class ServerConstants
{
    /**
     * Result of a read operation
     */
    enum ReadStatus
    {
        EOF,
        NO_BYTES,
        HAS_BYTES
    }

    /**
     * The three states for a {@link PeerEndpoint} object
     */
    enum EndpointState
    {
        /** initial state: raw socket connection */
        CONNECTED,
        /** registration (welcome) message was fully sent to a relay client (peer) */
        REGISTERED,
        /** Relay client sent a 'bind' message asking to send data to a destination */
        BOUND
    }
}
