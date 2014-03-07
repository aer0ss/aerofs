/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.zephyr.client.handlers;

import com.aerofs.zephyr.client.IZephyrSignallingClient;
import com.aerofs.zephyr.client.IZephyrSignallingService;
import com.aerofs.zephyr.client.exceptions.ExHandshakeFailed;
import com.aerofs.zephyr.proto.Zephyr.ZephyrHandshake;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.util.Timer;

import java.util.concurrent.TimeUnit;

/**
 * A convenience handler that encapsulates and holds reference to
 * the individual handlers that implement portions of the zephyr protocol.
 * Also contains convenience functions to find the state of the
 * underlying zephyr connection state.
 */
public final class ZephyrProtocolHandler extends SimpleChannelHandler implements IZephyrSignallingClient
{
    private static final String HANDSHAKE_HANDLER_NAME = "zephyr_handshake";
    private static final String REGISTRATION_DECODER_HANDLER_NAME = "zephyr_registration_decoder";

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
        pipeline.addAfter(ourHandlerName, REGISTRATION_DECODER_HANDLER_NAME, new ZephyrRegistrationDecoder());

        ctx.getPipeline().remove(this); // finish by removing ourself

        super.channelOpen(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        throw new IllegalStateException("handler should not be in pipeline during active communication");
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        throw new IllegalStateException("handler should not be in pipeline during active communication");
    }

    // Although the zephyrHandshakeHandler is removed from the pipeline
    // we always hold on to a reference to it. This allows us to detect renegotiations
    // for an existing channel
    @Override
    public void processIncomingZephyrSignallingMessage(ZephyrHandshake incoming)
            throws ExHandshakeFailed
    {
        zephyrHandshakeHandler.processIncomingZephyrSignallingMessage(incoming);
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
