/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.jingle.routing;

/**
 * Holds the number of times a connection has been established to the stored
 * {@link com.aerofs.daemon.transport.jingle.IConnectionService} on behalf of a peer.
 */
class DIDPipeConnectionCounter
{
    /**
     * Constructor
     * <br/>
     * <br/>
     * Initial connection-count for <code>p</code> is set to 0
     *
     * @param p {@link com.aerofs.daemon.transport.jingle.IConnectionService} for which the connection count is being maintained
     */
    DIDPipeConnectionCounter(IConnectionService p)
    {
        _p = p;
    }

    /**
     * @return {@link com.aerofs.daemon.transport.jingle.IConnectionService} this object represents
     */
    IConnectionService p()
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
     * Increments the contained {@link com.aerofs.daemon.transport.jingle.IConnectionService} object's reconnection count
     */
    void increment_()
    {
        ++_connSeqNum;
    }

    private final IConnectionService _p;

    private int _connSeqNum = 0;
}
