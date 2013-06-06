/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.zephyr.client.pipeline;

import com.aerofs.zephyr.client.IZephyrChannelStats;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;

import java.util.concurrent.atomic.AtomicLong;

final class ChannelStatsHandler extends SimpleChannelHandler implements IZephyrChannelStats
{
    private AtomicLong bytesSent = new AtomicLong(0);
    private AtomicLong bytesReceived = new AtomicLong(0);

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (e.getMessage() instanceof ChannelBuffer) {
            ChannelBuffer incoming = (ChannelBuffer) e.getMessage();
            bytesReceived.addAndGet(incoming.readableBytes());
        }

        super.messageReceived(ctx, e);
    }

    @Override
    public synchronized void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e)
            throws Exception
    {
        bytesSent.addAndGet(e.getWrittenAmount());
        super.writeComplete(ctx, e);
    }

    @Override
    public long getBytesSent()
    {
        return bytesSent.get();
    }

    @Override
    public long getBytesReceived()
    {
        return bytesReceived.get();
    }
}
