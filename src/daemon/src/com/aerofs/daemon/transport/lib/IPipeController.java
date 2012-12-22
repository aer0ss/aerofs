/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.lib;

import java.io.ByteArrayInputStream;

import com.aerofs.daemon.transport.xmpp.IPipe;
import com.aerofs.base.id.DID;
import com.aerofs.proto.Transport.PBTPHeader;

/**
 * Implemented by classes that want to control one or more {@link IPipe}
 * instances. This interface <em>should be the only way</em> the managed
 * <code>IPipe</code> instances communicate with their controller
 */
public interface IPipeController
{
    /**
     * An <code>IPipe</code> instance should call this method when a connection
     * is established to a peer
     *
     * @param did {@link DID} of the peer the <code>IPipe</code> connected to
     * @param type Whether or not the connection to the peer is readable or
     * both readable and writable
     * @param pipe instance of <code>IPipe</code> on which the connection was made
     */
    public void peerConnected(DID did, IPipe.ConnectionType type, IPipe pipe);

    /**
     * An <code>IPipe</code> instance should call this method when a connection
     * to a peer is broken (whether by error or on purpose)
     *
     * @param did {@link DID} of the peer the <code>IPipe</code> disconnected from
     * @param pipe instance of <code>IPipe</code> on which the disconnection happened
     */
    public void peerDisconnected(DID did, IPipe pipe);

    /**
     * An <code>IPipe</code> instance should call this method when it wants the
     * <code>IPipeController</code> to process a control message that came from
     * the peer on a <em>unicast</em> pipe
     *
     * @param did {@link DID} of the peer that sent the control message
     * @param hdr {@link PBTPHeader} control message receieved from the peer
     */
    public void processUnicastControl(DID did, PBTPHeader hdr);

    /**
     * An <code>IPipe</code> instance should call this method when it wants the
     * <code>IPipeController</code> to process an incoming payload message from
     * a peer
     *
     * @param did {@link DID} of the peer that sent the payload
     * @param hdr {@link PBTPHeader} payload message header received from the peer
     * @param bodyis {@link ByteArrayInputStream} wrapping the payload message body
     * @param wirelen number of bytes required to transmit this payload message
     */
    public void processUnicastPayload(DID did, PBTPHeader hdr, ByteArrayInputStream bodyis, int wirelen);

    /**
     * An <code>IPipe</code> instance should call this method when it wants the
     * <code>IPipeController</code> to close the existing streams for a peer. To
     * close both <code>inbound</code> and <code>outbound</code> streams, simply
     * set both these parameters to <code>true</code>, <code>true</code>.
     *
     * @param did {@link DID} of the peer for which existing streams should be closed
     * @param outbound <code>true</code> if all outbound streams should be closed,
     * <code>false</code> if they should remain open
     * @param inbound <code>true</code> if all inbound streams should be closed,
     * <code>false</code> if they should remain open
     */
    // FIXME: remove this interface method
    public void closePeerStreams(DID did, boolean outbound, boolean inbound);
}
