package com.aerofs.daemon.transport.xmpp.zephyr.client.netty.message;

import org.jboss.netty.buffer.ChannelBuffer;

public abstract class AbstractZephyrMessage
{
    public final ChannelBuffer payload;

    public AbstractZephyrMessage(ChannelBuffer p)
    {
        payload = p;
    }
}
