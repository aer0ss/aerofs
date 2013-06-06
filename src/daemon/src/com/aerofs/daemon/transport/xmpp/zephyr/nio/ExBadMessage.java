/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.nio;

/**
 * Thrown when either a {@link ZephyrClientState} receives an unexpected or unrecognized message
 * type or format.
 */
public class ExBadMessage extends Exception
{
    public ExBadMessage(String expectedMsgType)
    {
        super("received unexpected message instead of" + expectedMsgType);
    }

    /** serialization id */
    private static final long serialVersionUID = 1L;
}
