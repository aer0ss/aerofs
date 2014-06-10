/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.xray.server.core;

/**
 * Thrown when an {@link com.aerofs.xray.server.PeerEndpoint} attempts to bind to a PeerEndpoint that
 * has already been previously bound
 */
public class ExAlreadyBound extends Exception
{
    public ExAlreadyBound(int previd, int newid)
    {
        super("attempt to rebind prev:"+ previd + " new:" + newid);
        _previd = previd;
        _newid = newid;
    }

    public int getPrevid()
    {
        return _previd;
    }

    public int getNewid()
    {
        return _newid;
    }

    /** previous id to which the client/connection was bound */
    private final int _previd;

    /** id we are now attempting the client/connection to */
    private final int _newid;

    /** serialization id */
    private static final long serialVersionUID = 1L;
}
