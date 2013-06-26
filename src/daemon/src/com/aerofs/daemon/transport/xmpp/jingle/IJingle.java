/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp.jingle;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;

import java.io.ByteArrayInputStream;

/**
 * Implemented by a class that acts as a mediator between the implementation classes
 * in the <code>jingle</code> package and an instance of {@link com.aerofs.daemon.transport.lib.IConnectionServiceListener}
 */
interface IJingle
{
    /**
     * @see com.aerofs.daemon.transport.lib.ITransportStats#addBytesReceived(long)
     * Simple pass-through from internal <code>jingle</code> components to an
     * <code>IJingle</code> implementation that will route the arguments to an
     * instance of <code>ITransportStats</code>
     */
    void addBytesRx(long bytesrx);

     /**
     * @see com.aerofs.daemon.transport.lib.ITransportStats#addBytesSent(long)
     * Simple pass-through from internal <code>jingle</code> components to an
     * <code>IJingle</code> implementation that will route the arguments to an
     * instance of <code>ITransportStats</code>
     */
    void addBytesTx(long bytestx);

    /**
     * @see com.aerofs.daemon.transport.lib.IConnectionServiceListener#onDeviceConnected(DID, com.aerofs.daemon.transport.xmpp.IConnectionService)
     * Simple pass-through from internal <code>jingle</code> components to an
     * <code>IJingle</code> implementation that will route the call to an
     * instance of <code>IConnectionServiceListener</code>
     */
    void onDeviceConnected(DID did);

    /**
     * @see com.aerofs.daemon.transport.lib.IConnectionServiceListener#onDeviceDisconnected(DID, com.aerofs.daemon.transport.xmpp.IConnectionService)
     * Simple pass-through from internal <code>jingle</code> components to an
     * <code>IJingle</code> implementation that will route the call to an
     * instance of <code>IConnectionServiceListener</code>
     */
    void onDeviceDisconnected(DID did);

    /**
     * @see com.aerofs.daemon.transport.lib.IConnectionServiceListener#onIncomingMessage(com.aerofs.base.id.DID, java.io.InputStream, int)
     * Simple pass-through from internal <code>jingle</code> components to an
     * <code>IJingle</code> implementation that will route the arguments to an
     * instance of <code>IConnectionServiceListener</code>
     */
    public void onIncomingMessage(DID did, UserID userID, ByteArrayInputStream packet, int wirelen);
}
