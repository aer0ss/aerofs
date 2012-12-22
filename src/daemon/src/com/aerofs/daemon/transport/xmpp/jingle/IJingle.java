/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp.jingle;

import com.aerofs.base.id.DID;

import java.io.ByteArrayInputStream;

import static com.aerofs.proto.Transport.PBTPHeader;

/**
 * Implemented by a class that acts as a mediator between the implementation classes
 * in the <code>jingle</code> package and an instance of {@link IPipeController}
 */
interface IJingle
{
    /**
     * @see INetworkStats#addBytesRx(long)
     * Simple pass-through from internal <code>jingle</code> components to an
     * <code>IJingle</code> implementation that will route the arguments to an
     * instance of <code>INetworkStats</code>
     */
    void addBytesRx(long bytesrx);

     /**
     * @see INetworkStats#addBytesTx(long)
     * Simple pass-through from internal <code>jingle</code> components to an
     * <code>IJingle</code> implementation that will route the arguments to an
     * instance of <code>INetworkStats</code>
     */
    void addBytesTx(long bytestx);

    /**
     * @see IPipeController#peerConnected(DID, IPipe)
     * Simple pass-through from internal <code>jingle</code> components to an
     * <code>IJingle</code> implementation that will route the call to an
     * instance of <code>IPipeController</code>
     */
    void peerConnected(DID did);

    /**
     * @see IPipeController#peerDisconnected(DID, IPipe)
     * Simple pass-through from internal <code>jingle</code> components to an
     * <code>IJingle</code> implementation that will route the call to an
     * instance of <code>IPipeController</code>
     */
    void peerDisconnected(DID did);

    /**
     * @see IPipeController#processUnicastControl(DID, PBTPHeader)
     * Simple pass-through from internal <code>jingle</code> components to an
     * <code>IJingle</code> implementation that will route the arguments to an
     * instance of <code>IPipeController</code>
     */
    public void processUnicastControl(DID did, PBTPHeader hdr);

    /**
     * @see IPipeController#processUnicastPayload(DID, PBTPHeader, ByteArrayInputStream, int)
     * Simple pass-through from internal <code>jingle</code> components to an
     * <code>IJingle</code> implementation that will route the arguments to an
     * instance of <code>IPipeController</code>
     */
    public void processUnicastPayload(DID did, PBTPHeader hdr, ByteArrayInputStream bodyis, int wirelen);


    /**
     * @see IPipeController#closePeerStreams(DID, boolean, boolean)
     * Simple pass-through from internal <code>jingle</code> components to an
     * <code>IJingle</code> implementation that will route the arguments to an
     * instance of <code>IPipeController</code>
     */
    public void closePeerStreams(DID did, boolean outbound, boolean inbound);
}
