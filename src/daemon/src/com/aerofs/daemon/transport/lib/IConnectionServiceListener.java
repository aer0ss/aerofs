/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.transport.xmpp.IConnectionService;

import java.io.InputStream;

/**
 * Implemented by classes that want to control one or more {@link com.aerofs.daemon.transport.xmpp.IConnectionService}
 * instances. This interface <em>should be the only way</em> the managed
 * <code>IConnectionService</code> instances communicate with their controller
 */
public interface IConnectionServiceListener
{
    /**
     * An <code>IConnectionService</code> instance should call this method when a connection
     * is established to a peer
     *
     * @param did {@link com.aerofs.base.id.DID} of the peer the <code>IConnectionService</code> connected to
     * @param connectionService instance of <code>IConnectionService</code> on which the connection was made
     */
    public void onDeviceConnected(DID did, IConnectionService connectionService);

    /**
     * An <code>IConnectionService</code> instance should call this method when a connection
     * to a peer is broken (whether by error or on purpose)
     *
     * @param did {@link DID} of the peer the <code>IConnectionService</code> disconnected from
     * @param connectionService instance of <code>IConnectionService</code> on which the disconnection happened
     */
    public void onDeviceDisconnected(DID did, IConnectionService connectionService);

    /**
     * An <code>IConnectionService</code> instance should call this method when a
     * message is received from a peer
     *
     * @param did {@link DID} of the peer from which the message was received
     * @param userID {@link UserID} of the peer from which the message was received
     * @param packet {@link InputStream} that can be used to consume the incoming message
     * @param wirelen number of bytes this message took on the wire
     */
    public void onIncomingMessage(DID did, UserID userID, InputStream packet, int wirelen);
}
