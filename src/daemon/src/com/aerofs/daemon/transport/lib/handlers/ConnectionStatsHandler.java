/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.rocklog.RockLog;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.aerofs.daemon.lib.DaemonParam.SLOW_CONNECT;
import static com.aerofs.daemon.transport.lib.TransportDefects.DEFECT_NAME_CONNECT_FAILED;
import static com.aerofs.daemon.transport.lib.TransportDefects.DEFECT_NAME_SLOW_CONNECT;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public final class ConnectionStatsHandler extends SimpleChannelHandler
{
    private final String _transportId;
    private final RockLog _rockLog;
    private final Timer _timer;
    private final AtomicBoolean _wasConnected = new AtomicBoolean(false);

    private volatile long _channelOpenTime = 0;

    public ConnectionStatsHandler(String transportId, RockLog rockLog, Timer timer)
    {
        _transportId = transportId;
        _rockLog = rockLog;
        _timer = timer;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        _channelOpenTime = System.currentTimeMillis();

        final Channel channel = ctx.getChannel();

        _timer.newTimeout(new TimerTask()
        {
            @Override
            public void run(Timeout timeout)
                    throws Exception
            {
                if (!_wasConnected.get() && channel.isOpen()) {
                    sendRockLogDefect(
                            DEFECT_NAME_SLOW_CONNECT,
                            String.format("establishing a connection took over %s %s", SLOW_CONNECT, MILLISECONDS),
                            channel);
                }
            }
        }, SLOW_CONNECT, MILLISECONDS);

        super.channelOpen(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        _wasConnected.set(true);

        ctx.getPipeline().remove(this);

        super.channelConnected(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        String message;

        long channelOpenTime = _channelOpenTime;
        long currentTime = System.currentTimeMillis();
        if (channelOpenTime != 0) {
            message = String.format("channel closed without connecting after %s %s", currentTime - _channelOpenTime, MILLISECONDS);
        } else {
            message = String.format("channel closed without connecting");
        }

        if (!_wasConnected.get()) {
            sendRockLogDefect(DEFECT_NAME_CONNECT_FAILED, message, ctx.getChannel());
        }

        super.channelClosed(ctx, e);
    }

    private void sendRockLogDefect(String defectName, String defectMessage, Channel channel)
    {
        _rockLog.newDefect(defectName)
                .setMessage(defectMessage)
                .addData("transport", _transportId)
                .addData("channel", channel.toString())
                .addData("address", channel.getRemoteAddress())
                .send();
    }
}
