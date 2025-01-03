/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.daemon.transport.lib.TransportStats;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;

import java.util.concurrent.atomic.AtomicLong;

public final class IOStatsHandler extends SimpleChannelHandler
{
    // this channel only

    private final long channelCreationTime = System.currentTimeMillis();
    private final AtomicLong bytesSent = new AtomicLong(0);
    private final AtomicLong bytesReceived = new AtomicLong(0);

    // across multiple channels

    private final TransportStats transportStats;

    public IOStatsHandler(TransportStats transportStats)
    {
        this.transportStats = transportStats;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (e.getMessage() instanceof ChannelBuffer) {
            long n = ((ChannelBuffer) e.getMessage()).readableBytes();
            bytesReceived.getAndAdd(n);
            transportStats.addBytesReceived(n);
        }

        ctx.sendUpstream(e);
    }

    @Override
    public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e)
            throws Exception
    {
        bytesSent.getAndAdd(e.getWrittenAmount());
        transportStats.addBytesSent(e.getWrittenAmount());

        ctx.sendUpstream(e);
    }

    public long getBytesSentOnChannel()
    {
        return bytesSent.get();
    }

    public long getBytesReceivedOnChannel()
    {
        return bytesReceived.get();
    }

    public long getChannelCreationTime()
    {
        return channelCreationTime;
    }
}
