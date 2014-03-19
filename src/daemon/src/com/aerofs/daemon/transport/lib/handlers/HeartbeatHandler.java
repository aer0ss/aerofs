/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.daemon.transport.lib.TransportProtocolUtil;
import com.aerofs.proto.Transport.PBHeartbeat;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;
import static org.jboss.netty.channel.Channels.future;
import static org.jboss.netty.channel.Channels.write;

/**
 * Handler that sends heartbeats to a remote peer at predefined intervals.
 */
public final class HeartbeatHandler extends SimpleChannelHandler
{
    private static final int MINIMUM_ADDITIONAL_INTERVAL = 20;

    private static final Logger l = LoggerFactory.getLogger(HeartbeatHandler.class);

    private final Random random = new Random();
    private final long heartbeatInterval;
    private final int maxFailedHeartbeats;
    private final Timer heartbeatTimer; // thread-safe

    private final ElapsedTimer outgoingMessageTimer = new ElapsedTimer(); // protected by this
    private int unansweredHeartbeatCount; // protected by this
    private int lastSentHeartbeatId; // protected by this

    public HeartbeatHandler(long heartbeatInterval, int maxFailedHeartbeats, Timer heartbeatTimer)
    {
        this.heartbeatInterval = heartbeatInterval;
        this.maxFailedHeartbeats = maxFailedHeartbeats;
        this.heartbeatTimer = heartbeatTimer;
    }

    //
    // modify heartbeat state
    //

    private synchronized void onHeartbeatSent(int heartbeatId)
    {
        lastSentHeartbeatId = heartbeatId;
        unansweredHeartbeatCount++;
    }

    private synchronized void onHeartbeatReceived(int heartbeatId) {
        // we're only interested in resetting the counter
        // if we receive a response to the latest heartbeat sent
        // out in a timely fashion
        if (heartbeatId == lastSentHeartbeatId) {
            l.debug("received heartbeat id:{} - heartbeat count reset", heartbeatId);
            unansweredHeartbeatCount = 0;
        } else {
            l.debug("received heartbeat id:{}", heartbeatId);
        }
    }

    private synchronized int getUnansweredHeartbeatCount() {
        return unansweredHeartbeatCount;
    }

    //
    // ChannelHandler methods
    //

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        scheduleHeartbeat(ctx, heartbeatInterval);
        super.channelConnected(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (!(e.getMessage() instanceof TransportMessage)) {
            super.messageReceived(ctx, e);
            return;
        }

        TransportMessage msg = (TransportMessage) e.getMessage();
        Type type = msg.getHeader().getType();

        if (!isHeartbeatMessage(type)) {
            super.messageReceived(ctx, e);
            return;
        }

        checkState(isHeartbeatMessage(type), "unexpected type:%s", type.name());

        int heartbeatId = msg.getHeader().getHeartbeat().getHeartbeatId();

        if (type == Type.HEARTBEAT_CALL) {
            // a remote peer wants to check if the connection is live
            PBTPHeader reply = PBTPHeader
                    .newBuilder()
                    .setType(Type.HEARTBEAT_REPLY)
                    .setHeartbeat(PBHeartbeat
                            .newBuilder()
                            .setHeartbeatId(heartbeatId))
                    .build();
            write(ctx, future(ctx.getChannel()), TransportProtocolUtil.newControl(reply));
            l.debug("respond to heartbeat id:{}", heartbeatId);
        } else {
            // we've received a heartbeat reply
            onHeartbeatReceived(heartbeatId);
        }
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        synchronized (this) {
            outgoingMessageTimer.restart();
        }

        super.writeRequested(ctx, e);
    }

    private void scheduleHeartbeat(final ChannelHandlerContext ctx, long interval)
    {
        final Channel channel = ctx.getChannel();

        if (channel.getCloseFuture().isDone()) {
            l.debug("channel closed - skip heartbeat reschedule");
            return;
        }

        l.debug("schedule heartbeat after {}ms", interval);

        heartbeatTimer.newTimeout(new TimerTask()
        {
            @Override
            public void run(Timeout timeout)
                    throws Exception
            {
                int unansweredHeartbeats = getUnansweredHeartbeatCount();

                if (unansweredHeartbeats >= maxFailedHeartbeats) {
                    Channels.fireExceptionCaughtLater(channel, new ExHeartbeatTimedOut(
                            "fail " + maxFailedHeartbeats + " consecutive heartbeats"));
                    return;
                }

                long timeSinceLastMessage;
                synchronized (HeartbeatHandler.this) {
                    timeSinceLastMessage = outgoingMessageTimer.elapsed();
                }

                long scheduleAfter;

                if (unansweredHeartbeats == 0 && (timeSinceLastMessage < heartbeatInterval)) {
                    scheduleAfter = Math.max(MINIMUM_ADDITIONAL_INTERVAL, heartbeatInterval - timeSinceLastMessage);
                } else {
                    handleHeartbeatTimeout();
                    scheduleAfter = heartbeatInterval;
                }

                scheduleHeartbeat(ctx, scheduleAfter);
            }

            private void handleHeartbeatTimeout()
            {
                int heartbeatId = random.nextInt();

                PBTPHeader heartbeat = PBTPHeader
                        .newBuilder()
                        .setType(Type.HEARTBEAT_CALL)
                        .setHeartbeat(PBHeartbeat
                                .newBuilder()
                                .setHeartbeatId(heartbeatId))
                        .build();

                write(ctx, future(channel), TransportProtocolUtil.newControl(heartbeat));

                onHeartbeatSent(heartbeatId);

                l.debug("send heartbeat id:{}", heartbeatId);
            }
        }, interval, TimeUnit.MILLISECONDS);
    }

    private static boolean isHeartbeatMessage(Type type)
    {
        return type == Type.HEARTBEAT_CALL || type == Type.HEARTBEAT_REPLY;
    }
}
