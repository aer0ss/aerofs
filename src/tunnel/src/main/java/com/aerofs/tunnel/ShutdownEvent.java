package com.aerofs.tunnel;

import org.jboss.netty.channel.*;

public class ShutdownEvent implements ChannelEvent {

    private final ChannelFuture _future;
    private final Channel _channel;

    public ShutdownEvent(Channel channel, ChannelFuture future) {
        _channel = channel;
        _future = future;
    }

    @Override
    public Channel getChannel() {
        return _channel;
    }

    @Override
    public ChannelFuture getFuture() {
        return _future;
    }
}
