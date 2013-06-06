/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.nio;

import com.aerofs.base.id.DID;

/**
 * Thrown whenever any code requests a {@link com.aerofs.daemon.transport.xmpp.zephyr.nio.ZephyrClientContext} that doesn't
 * exist, or is (maybe temporarily) in an invalid state.
 */
public class ExInvalidZephyrClient extends Exception
{
    public ExInvalidZephyrClient(String msg, DID did)
    {
        super(msg);
        _did = did;
    }

    public DID getDid()
    {
        return _did;
    }

    /** {@link DID} for the invalid ZephyrClientContext */
    private final DID _did;

    /** serialization id */
    private static final long serialVersionUID = 1L;
}
