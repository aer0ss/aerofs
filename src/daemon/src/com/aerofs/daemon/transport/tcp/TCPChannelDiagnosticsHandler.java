/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.daemon.transport.lib.IChannelDiagnosticsHandler;
import com.aerofs.daemon.transport.lib.TransportUtil;
import com.aerofs.daemon.transport.lib.handlers.HandlerMode;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.proto.Diagnostics.TCPChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.SimpleChannelHandler;

import java.net.InetSocketAddress;

@Sharable
final class TCPChannelDiagnosticsHandler extends SimpleChannelHandler implements IChannelDiagnosticsHandler
{
    private final HandlerMode mode;

    public TCPChannelDiagnosticsHandler(HandlerMode mode)
    {
        this.mode = mode;
    }

    @Override
    public TCPChannel getDiagnostics(Channel channel)
    {
        IOStatsHandler ioStatsHandler = channel.getPipeline().get(IOStatsHandler.class);
        InetSocketAddress address = (InetSocketAddress) channel.getRemoteAddress();

        return TCPChannel
                .newBuilder()
                .setState(TransportUtil.getChannelState(channel))
                .setBytesSent(ioStatsHandler.getBytesSentOnChannel())
                .setBytesReceived(ioStatsHandler.getBytesReceivedOnChannel())
                .setLifetime(System.currentTimeMillis() - ioStatsHandler.getChannelCreationTime())
                .setOriginator(mode == HandlerMode.CLIENT)
                .setRemoteAddress(TransportUtil.fromInetSockAddress(address, false))
                .build();
    }
}
