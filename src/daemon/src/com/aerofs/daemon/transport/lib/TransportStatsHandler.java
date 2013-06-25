package com.aerofs.daemon.transport.lib;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;

@org.jboss.netty.channel.ChannelHandler.Sharable
public final class TransportStatsHandler extends SimpleChannelHandler
{
    private final ITransportStats _transportStats;

    public TransportStatsHandler(ITransportStats transportStats)
    {
        _transportStats = transportStats;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (e.getMessage() instanceof ChannelBuffer) {
            ChannelBuffer buffer = (ChannelBuffer) e.getMessage();
            _transportStats.addBytesReceived(buffer.readableBytes());
        }

        super.messageReceived(ctx, e);
    }

    @Override
    public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e)
            throws Exception
    {
        _transportStats.addBytesSent(e.getWrittenAmount());
        super.writeComplete(ctx, e);
    }
}
