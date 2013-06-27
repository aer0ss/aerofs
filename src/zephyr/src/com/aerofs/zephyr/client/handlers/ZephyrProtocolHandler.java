/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.zephyr.client.handlers;

import com.aerofs.zephyr.client.IZephyrSignallingClient;
import com.aerofs.zephyr.client.IZephyrSignallingService;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.util.Timer;

import java.util.concurrent.TimeUnit;

public final class ZephyrProtocolHandler extends SimpleChannelHandler
{
    private static final String HANDSHAKE_HANDLER_NAME = "zephyr_handshake";
    private static final String DECODER_HANDLER_NAME = "zephyr_decoder";
    private static final String ENCODER_HANDLER_NAME = "zephyr_encoder";

    private final ZephyrHandshakeHandler zephyrHandshakeHandler;

    public ZephyrProtocolHandler(IZephyrSignallingService signallingService, Timer handshakeTimer, long handshakeTimeout, TimeUnit handshakeTimeoutTimeUnit)
    {
        this.zephyrHandshakeHandler = new ZephyrHandshakeHandler(signallingService, handshakeTimer, handshakeTimeout, handshakeTimeoutTimeUnit);
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        ChannelPipeline pipeline = ctx.getPipeline();

        // IMPORTANT: channelOpen goes _upstream_ !
        // this means you have to add the handlers _after_ ourself, in order for the
        // fireChannelOpen to work

        String ourHandlerName = ctx.getName();
        pipeline.addAfter(ourHandlerName, HANDSHAKE_HANDLER_NAME, zephyrHandshakeHandler);
        pipeline.addAfter(ourHandlerName, DECODER_HANDLER_NAME, new ZephyrFrameDecoder());
        pipeline.addAfter(ourHandlerName, ENCODER_HANDLER_NAME, new ZephyrFrameEncoder());

        ctx.getPipeline().remove(this); // finish by removing ourself

        super.channelOpen(ctx, e);
    }

    public IZephyrSignallingClient getZephyrSignallingClient()
    {
        return zephyrHandshakeHandler;
    }

    public boolean hasHandshakeCompleted()
    {
        return zephyrHandshakeHandler.hasHandshakeCompleted();
    }

    public long getRemoteZid()
    {
        return zephyrHandshakeHandler.getRemoteZid();
    }

    public long getLocalZid()
    {
        return zephyrHandshakeHandler.getLocalZid();
    }
}
