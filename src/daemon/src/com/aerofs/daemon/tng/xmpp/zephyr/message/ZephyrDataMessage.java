/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr.message;

import org.jboss.netty.buffer.ChannelBuffer;

public class ZephyrDataMessage implements IZephyrMessage
{
    public final ChannelBuffer payload;

    public ZephyrDataMessage(ChannelBuffer p)
    {
        payload = p;
    }

}
