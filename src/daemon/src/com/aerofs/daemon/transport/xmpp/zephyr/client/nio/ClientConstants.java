/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio;

/**
 * ClientConstants that are relevant only to {@link ZephyrClientContext} and
 * {@link ZephyrClientState} implementation methods.
 */
public final class ClientConstants
{
    /** length of the header each ZephyrClient slaps on to all outgoing packets */
    public static final int ZEPHYR_CLIENT_HDR_LEN = 2 * (Integer.SIZE / 8); // C.CORE_MAGIC + payload length
}
