/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.ex.ExTimeout;
import com.aerofs.daemon.transport.lib.TransportUtil;
import com.aerofs.proto.Diagnostics.ChannelState;
import com.google.common.base.Preconditions;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * A handler to automatically close connections that are not verified within a given time.
 * On channelOpen, start a timer; on channel verification or close, cancel the timer.
 * If the timer fires, drop the channel.
 */
public final class ConnectTimeoutHandler extends SimpleChannelHandler
{
    private static final Logger l = LoggerFactory.getLogger(ConnectTimeoutHandler.class);

    private final Timer timer;
    private final long connectTimeout;
    private Timeout timeout;

    public ConnectTimeoutHandler(long channelConnectTimeout, Timer timer)
    {
        this.connectTimeout = channelConnectTimeout;
        this.timer = timer;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception
    {
        this.timeout = createTimeout(ctx);
        super.channelOpen(ctx, e);
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        if (timeout != null) timeout.cancel();
        ctx.getPipeline().remove(this);
        super.channelDisconnected(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        if (timeout != null) timeout.cancel();
        ctx.getPipeline().remove(this);
        super.channelConnected(ctx, e);
    }

    Timeout createTimeout(final ChannelHandlerContext ctx)
    {
        return timer.newTimeout(timeout1 -> {
            Channel channel = ctx.getChannel();
            Preconditions.checkNotNull(channel);

            l.debug("connect timer {}:{}", TransportUtil.hexify(channel),
                    TransportUtil.getChannelState(channel));

            if (TransportUtil.getChannelState(channel) != ChannelState.VERIFIED) {
                l.warn("Timeout waiting for verification {}", TransportUtil.hexify(channel));
                Channels.fireExceptionCaughtLater(channel,
                        new ExTimeout("Timed out waiting for CName verification"));
            }
        }, connectTimeout, TimeUnit.MILLISECONDS);
    }
}
