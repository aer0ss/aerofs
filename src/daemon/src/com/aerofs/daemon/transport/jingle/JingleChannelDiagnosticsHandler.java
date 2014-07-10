/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.daemon.transport.lib.IChannelDiagnosticsHandler;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.daemon.transport.lib.TransportUtil;
import com.aerofs.daemon.transport.lib.handlers.HandlerMode;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.proto.Diagnostics.JingleChannel;
import com.aerofs.proto.Diagnostics.JingleChannel.Builder;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.SimpleChannelHandler;

@Sharable
final class JingleChannelDiagnosticsHandler extends SimpleChannelHandler implements IChannelDiagnosticsHandler
{
    private final HandlerMode mode;
    private final IRoundTripTimes roundTripTimes;

    public JingleChannelDiagnosticsHandler(HandlerMode mode, IRoundTripTimes roundTripTimes)
    {
        this.mode = mode;
        this.roundTripTimes = roundTripTimes;
    }

    @Override
    public JingleChannel getDiagnostics(Channel channel)
    {
        IOStatsHandler ioStatsHandler = channel.getPipeline().get(IOStatsHandler.class);

        Builder builder = JingleChannel.newBuilder()
                .setState(TransportUtil.getChannelState(channel))
                .setBytesSent(ioStatsHandler.getBytesSentOnChannel())
                .setBytesReceived(ioStatsHandler.getBytesReceivedOnChannel())
                .setLifetime(System.currentTimeMillis() - ioStatsHandler.getChannelCreationTime())
                .setOriginator(mode == HandlerMode.CLIENT);

        Long rtt = roundTripTimes.getMicros(channel.getId());
        if (rtt != null) {
            builder.setRoundTripTime(rtt);
        }

        return builder.build();
    }
}
