/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;

import java.io.InputStream;

interface IConnectionServiceListener
{
    public void onDeviceConnected(DID did);
    public void onDeviceDisconnected(DID did);

    /**
     * An <code>IConnectionService</code> instance should call this method when a
     * message is received from a peer
     *
     * @param did {@link com.aerofs.base.id.DID} of the peer from which the message was received
     * @param userID {@link com.aerofs.base.id.UserID} of the peer from which the message was received
     * @param packet {@link java.io.InputStream} that can be used to consume the incoming message
     * @param wirelen number of bytes this message took on the wire
     */
    public void onIncomingMessage(DID did, UserID userID, InputStream packet, int wirelen);
}
