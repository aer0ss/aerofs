package com.aerofs.daemon.transport.xmpp.zephyr.client.netty.message;

import org.jboss.netty.buffer.ChannelBuffer;

public class ZephyrServerMessage extends AbstractZephyrMessage
{

    public ZephyrServerMessage(ChannelBuffer p)
    {
        super(p);
    }

}
