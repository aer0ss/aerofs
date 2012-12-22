/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.jingle;

import com.aerofs.base.id.DID;

/**
 * Implemented by a class that acts as a mediator between the implementation classes in the
 * <code>jingle</code> package and an instance of {@link com.aerofs.daemon.tng.base.IUnicastConnectionService}
 */
interface IJingle
{
    /**
     * @see com.aerofs.daemon.tng.base.INetworkStats#addBytesRx(long) Simple pass-through from
     *      internal <code>jingle</code> components to an <code>IJingle</code> implementation that
     *      will route the arguments to an instance of <code>INetworkStats</code>
     */
    void addBytesRx(long bytesrx);

    /**
     * @see com.aerofs.daemon.tng.base.INetworkStats#addBytesTx(long) Simple pass-through from
     *      internal <code>jingle</code> components to an <code>IJingle</code> implementation that
     *      will route the arguments to an instance of <code>INetworkStats</code>
     */
    void addBytesTx(long bytestx);

    /**
     * Simple pass-through from internal <code>jingle</code> components to an <code>IJingle</code>
     * implementation that will route the call to an instance of <code>JingleUnicastConnection</code>
     */
    void peerConnected(DID did);

    /**
     * Simple pass-through from internal <code>jingle</code> components to an <code>IJingle</code>
     * implementation that will route the call to an instance of <code>JingleUnicastConnection</code>
     */
    void peerDisconnected(DID did);


    void incomingConnection(DID did, ISignalThreadTask acceptTask);

    /**
     * Simple pass-through from internal <code>jingle</code> components to an <code>IJingle</code>
     * implementation that will route the call to an instance of <code>JingleUnicastConnection</code>
     */
    public void processData(DID did, byte[] data, int wirelen);


    /**
     * Simple pass-through from internal <code>jingle</code> components to an <code>IJingle</code>
     * implementation that will route the call to an instance of <code>JingleUnicastConnection</code>
     */
    public void closePeerStreams(DID did, boolean outbound, boolean inbound);
}
