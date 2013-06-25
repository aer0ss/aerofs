/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple Netty handler to count the amount of bytes that go through the pipeline
 */
public class ChannelStatsHandler extends SimpleChannelHandler
{
    private final AtomicLong _bytesSent = new AtomicLong();
    private final AtomicLong _bytesReceived = new AtomicLong();

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (e.getMessage() instanceof ChannelBuffer) {
            ChannelBuffer incoming = (ChannelBuffer) e.getMessage();
            _bytesReceived.addAndGet(incoming.readableBytes());
        }
        super.messageReceived(ctx, e);
    }

    @Override
    public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e)
            throws Exception
    {
        _bytesSent.addAndGet(e.getWrittenAmount());
        super.writeComplete(ctx, e);
    }

    public long getBytesSent()
    {
        return _bytesSent.get();
    }

    public long getBytesReceived()
    {
        return _bytesReceived.get();
    }
}
