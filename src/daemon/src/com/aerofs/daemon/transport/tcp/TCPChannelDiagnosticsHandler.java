/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.daemon.transport.lib.IChannelDiagnosticsHandler;
import com.aerofs.daemon.transport.lib.TransportDefects;
import com.aerofs.daemon.transport.lib.TransportUtil;
import com.aerofs.daemon.transport.lib.handlers.HandlerMode;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.proto.Diagnostics.TCPChannel;
import com.aerofs.rocklog.RockLog;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.SimpleChannelHandler;

import java.net.InetSocketAddress;

@Sharable
final class TCPChannelDiagnosticsHandler extends SimpleChannelHandler implements IChannelDiagnosticsHandler
{
    private final HandlerMode mode;
    private final RockLog rockLog;

    public TCPChannelDiagnosticsHandler(HandlerMode mode, RockLog rockLog)
    {
        this.mode = mode;
        this.rockLog = rockLog;
    }

    @Override
    public TCPChannel getDiagnostics(Channel channel)
    {
        IOStatsHandler ioStatsHandler = channel.getPipeline().get(IOStatsHandler.class);

        TCPChannel.Builder channelBuilder = TCPChannel.newBuilder();
        channelBuilder
                .setState(TransportUtil.getChannelState(channel))
                .setBytesSent(ioStatsHandler.getBytesSentOnChannel())
                .setBytesReceived(ioStatsHandler.getBytesReceivedOnChannel())
                .setLifetime(System.currentTimeMillis() - ioStatsHandler.getChannelCreationTime())
                .setOriginator(mode == HandlerMode.CLIENT);

        InetSocketAddress address = (InetSocketAddress) channel.getRemoteAddress();
        if (address != null) {
            channelBuilder.setRemoteAddress(TransportUtil.fromInetSockAddress(address, false));
        } else {
            // FIXME (AG): according to the netty codebase this can happen sometimes on Windows when the socket is closed. I'm suspicious.
            rockLog.newDefect(TransportDefects.DEFECT_NAME_NULL_REMOTE_ADDRESS).addData("state", TransportUtil.getChannelState(channel)).send();
        }

        return channelBuilder.build();
    }
}
