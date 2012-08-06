/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr.message;

import org.jboss.netty.buffer.ChannelBuffer;

public class ZephyrRegistrationMessage implements IZephyrMessage
{
    public final int zid;

    public ZephyrRegistrationMessage(int zid)
    {
        this.zid = zid;
    }

    public static ZephyrRegistrationMessage create(ChannelBuffer p)
    {
        assert p.readableBytes() >= 4;
        return new ZephyrRegistrationMessage(p.readInt());
    }

}
