/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.xray.server;

/**
 * Thrown when data has to be delivered to an invalid ZephyrClient
 */
public class ExInvalidPeerEndpoint extends Exception
{
    public ExInvalidPeerEndpoint(int id)
    {
        super("invalid endpoint:" + id);
        _id = id;
    }

    public int getInvalidId()
    {
        return _id;
    }

    /** id of the invalid ZephyrClient */
    private final int _id;

    /** serialization id */
    private static final long serialVersionUID = 1L;
}
