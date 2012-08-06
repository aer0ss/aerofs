package com.aerofs.daemon.transport.xmpp.zephyr.client.netty.exception;

import org.jboss.netty.channel.Channel;

public class ExZephyrAlreadyBound extends ExInvalidZephyrChannelAction
{
    private static final long serialVersionUID = 1L;

    public ExZephyrAlreadyBound(Channel channel, int oldZid, int newZid)
    {
        super(channel, "Old zid: " + oldZid + ", New zid: " + newZid);
    }

}
