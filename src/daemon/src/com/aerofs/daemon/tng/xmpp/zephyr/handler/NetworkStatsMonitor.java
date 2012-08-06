/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr.handler;

import com.aerofs.daemon.tng.base.INetworkStats;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

public class NetworkStatsMonitor extends SimpleChannelHandler
{

    private final INetworkStats _networkStats;

    public NetworkStatsMonitor(INetworkStats stats)
    {
        assert stats != null : ("INetworkStats is null in NetworkStatsMonitor");

        _networkStats = stats;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (e.getMessage() instanceof ChannelBuffer) {
            ChannelBuffer buffer = (ChannelBuffer) e.getMessage();

            // Record the number of bytes being read
            _networkStats.addBytesRx(buffer.readableBytes());
        }

        super.messageReceived(ctx, e);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (e.getMessage() instanceof ChannelBuffer) {
            ChannelBuffer buffer = (ChannelBuffer) e.getMessage();

            // Record the number of bytes being read
            _networkStats.addBytesTx(buffer.writableBytes());
        }

        super.writeRequested(ctx, e);
    }

}
