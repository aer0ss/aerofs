/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.daemon.transport.lib.IChannelDiagnosticsHandler;
import com.aerofs.daemon.transport.lib.TransportUtil;
import com.aerofs.daemon.transport.lib.handlers.HandlerMode;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.proto.Diagnostics.JingleChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.SimpleChannelHandler;

@Sharable
final class JingleChannelDiagnosticsHandler extends SimpleChannelHandler implements IChannelDiagnosticsHandler
{
    private final HandlerMode mode;

    public JingleChannelDiagnosticsHandler(HandlerMode mode)
    {
        this.mode = mode;
    }

    @Override
    public JingleChannel getDiagnostics(Channel channel)
    {
        IOStatsHandler ioStatsHandler = channel.getPipeline().get(IOStatsHandler.class);

        return JingleChannel
                .newBuilder()
                .setState(TransportUtil.getChannelState(channel))
                .setBytesSent(ioStatsHandler.getBytesSentOnChannel())
                .setBytesReceived(ioStatsHandler.getBytesReceivedOnChannel())
                .setLifetime(System.currentTimeMillis() - ioStatsHandler.getChannelCreationTime())
                .setOriginator(mode == HandlerMode.CLIENT)
                .build();
    }
}
