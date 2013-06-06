package com.aerofs.daemon.transport.xmpp.zephyr.netty;

import com.aerofs.daemon.transport.lib.ITransportStats;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;

final class TransportStatsHandler extends SimpleChannelHandler
{
    private final ITransportStats transportStats;

    public TransportStatsHandler(ITransportStats transportStats)
    {
        this.transportStats = transportStats;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (e.getMessage() instanceof ChannelBuffer) {
            ChannelBuffer buffer = (ChannelBuffer) e.getMessage();
            transportStats.addBytesReceived(buffer.readableBytes());
        }

        super.messageReceived(ctx, e);
    }

    @Override
    public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e)
            throws Exception
    {
        transportStats.addBytesSent(e.getWrittenAmount());
        super.writeComplete(ctx, e);
    }
}
