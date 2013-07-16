/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.jingle.routing;

import com.aerofs.base.id.DID;

import static com.aerofs.daemon.transport.jingle.routing.ErrorPipe.ERROR_PIPE;

/**
 * A stream cookie that is always returned by <code>send</code> calls to
 * a {@link ConnectionServiceWrapper}. It remembers the connection-count number of the
 * {@link com.aerofs.daemon.transport.jingle.IConnectionService} used to send a packet to a peer. A difference between
 * this stored connection count and one available in the system means that a
 * reconnection occurred.
 */
class DIDPipeCookie
{
    /**
     *
     * @param did
     */
    DIDPipeCookie(DID did)
    {
        assert did != null : ("invalid args");

        _did = did;
        _p = ERROR_PIPE;
    }

    /**
     *
     * @param did
     * @param p
     * @param routeSeqNum
     */
    DIDPipeCookie(DID did, IConnectionService p, int routeSeqNum)
    {
        assert did != null && p != null : ("invalid args");

        _did = did;
        _p = p;
        _connSeqNum = routeSeqNum;
        _set = true;
    }

    /**
     *
     * @param p
     * @param connSeqNum
     */
    void set_(IConnectionService p, int connSeqNum)
    {
        assert !_set : ("set_ already called");
        assert p != null : ("invalid route");

        _p = p;
        _connSeqNum = connSeqNum;
        _set = true;
    }

    /**
     * @return
     */
    public DID did()
    {
        return _did;
    }

    /**
     * @return
     */
    public IConnectionService p_()
    {
        return _p;
    }

    /**
     * @return
     */
    public int connSeqNum_()
    {
        return _connSeqNum;
    }

    /**
     *
     * @return
     */
    public boolean set_()
    {
        return _set;
    }

    private final DID _did;

    private IConnectionService _p;
    private int _connSeqNum;
    private boolean _set = false;
}
