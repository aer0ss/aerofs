package com.aerofs.daemon.transport.xmpp.zephyr.client.netty.message;

import org.jboss.netty.buffer.ChannelBuffer;

public class ZephyrClientMessage extends AbstractZephyrMessage
{

    public ZephyrClientMessage(ChannelBuffer p)
    {
        super(p);
    }

}
