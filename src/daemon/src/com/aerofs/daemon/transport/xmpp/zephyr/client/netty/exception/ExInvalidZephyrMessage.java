/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.netty.exception;

/**
 * TODO: Replace ExBadMessage with this when server code refactore
 */
public class ExInvalidZephyrMessage extends Exception
{
    public ExInvalidZephyrMessage(String msg)
    {
        super(msg);
    }

    /** serialization id */
    private static final long serialVersionUID = 1L;
}
