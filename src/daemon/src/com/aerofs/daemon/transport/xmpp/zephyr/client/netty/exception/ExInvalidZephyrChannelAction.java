package com.aerofs.daemon.transport.xmpp.zephyr.client.netty.exception;

import org.jboss.netty.channel.Channel;

public class ExInvalidZephyrChannelAction extends Exception
{
    private static final long serialVersionUID = 1L;

    private final Channel _channel;

    public ExInvalidZephyrChannelAction(Channel channel)
    {
        this(channel, "ExZephyrChannel");
    }

    public ExInvalidZephyrChannelAction(Channel channel, String message)
    {
        super(message);

        assert channel != null : ("Channel is null");
        _channel = channel;
    }

    public Channel getChannel()
    {
        return _channel;
    }

}
