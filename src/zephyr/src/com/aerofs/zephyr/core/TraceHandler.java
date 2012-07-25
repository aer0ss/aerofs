/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.zephyr.core;

import com.aerofs.lib.Loggers;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.slf4j.Logger;

public class TraceHandler extends SimpleChannelHandler
{
    private static final Logger LOGGER = Loggers.getLogger(TraceHandler.class);

    long _sending;
    long _sent;
    long _received;

    private Object getName(ChannelHandlerContext ctx)
    {
        return ctx.getChannel();
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        ChannelBuffer buffer = (ChannelBuffer)e.getMessage();
        _sending += buffer.readableBytes();
        LOGGER.info("{} send {}, total {}", new Object[] {
                getName(ctx), buffer.readableBytes(), _sending
        });
        super.writeRequested(ctx, e);
        if (!ctx.getChannel().isWritable()) {
            LOGGER.info("{} not writable", getName(ctx));
        }
    }

    @Override
    public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e)
            throws Exception
    {
        _sent += e.getWrittenAmount();
        LOGGER.info("{} sent {}, total {}", new Object[] {
                getName(ctx), e.getWrittenAmount(), _sent
        });
        super.writeComplete(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        ChannelBuffer buffer = (ChannelBuffer)e.getMessage();
        _received += buffer.readableBytes();
        LOGGER.info("{} recv {}, total {}", new Object[] {
                getName(ctx), buffer.readableBytes(), _received
        });
        super.messageReceived(ctx, e);
    }

    @Override
    public void setInterestOpsRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        LOGGER.info("{}", e);
        super.setInterestOpsRequested(ctx, e);
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        LOGGER.info("{} {}: {}", new Object[] {
                getName(ctx), e.getState(), e.getValue()
        });
        super.channelInterestChanged(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        LOGGER.info("{}", e);
        super.channelConnected(ctx, e);
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        LOGGER.info("{}", e);
        super.channelDisconnected(ctx, e);
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        LOGGER.info("{}", e);
        super.channelOpen(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        LOGGER.info("{}", e);
        super.channelClosed(ctx, e);
    }

    @Override
    public void connectRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        LOGGER.info("{}", e);
        super.connectRequested(ctx, e);
    }

    @Override
    public void disconnectRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        LOGGER.info("{}", e);
        super.disconnectRequested(ctx, e);
    }

    @Override
    public void closeRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        LOGGER.info("{}", e);
        super.closeRequested(ctx, e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception
    {
        LOGGER.warn(getName(ctx) + " error", e.getCause());
        super.exceptionCaught(ctx, e);
    }
}
