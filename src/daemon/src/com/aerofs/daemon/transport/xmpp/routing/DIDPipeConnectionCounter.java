/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp.routing;

import com.aerofs.daemon.transport.xmpp.IPipe;

/**
 * Holds the number of times a connection has been established to the stored
 * {@link IPipe} on behalf of a peer.
 */
class DIDPipeConnectionCounter
{
    /**
     * Constructor
     * <br/>
     * <br/>
     * Initial connection-count for <code>p</code> is set to 0
     *
     * @param p {@link IPipe} for which the connection count is being maintained
     */
    DIDPipeConnectionCounter(IPipe p)
    {
        _p = p;
    }

    /**
     * @return {@link IPipe} this object represents
     */
    IPipe p()
    {
        return _p;
    }

    /**
     * @return the number of times this route was 'connected' i.e. its
     * reconnection count
     */
    int connSeqNum_()
    {
        return _connSeqNum;
    }

    /**
     * Increments the contained {@link IPipe} object's reconnection count
     */
    void increment_()
    {
        ++_connSeqNum;
    }

    private final IPipe _p;

    private int _connSeqNum = 0;
}
