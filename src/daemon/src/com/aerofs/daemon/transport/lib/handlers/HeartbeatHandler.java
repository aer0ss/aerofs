/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.ids.DID;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.daemon.transport.lib.TransportProtocolUtil;
import com.aerofs.daemon.transport.lib.TransportUtil;
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
    private IRoundTripTimes roundTripTimes;

    public HeartbeatHandler(long heartbeatInterval, int maxFailedHeartbeats, Timer heartbeatTimer,
            IRoundTripTimes roundTripTimes)
    {
        this.heartbeatInterval = heartbeatInterval;
        this.maxFailedHeartbeats = maxFailedHeartbeats;
        this.heartbeatTimer = heartbeatTimer;
        this.roundTripTimes = roundTripTimes;
    }

    //
    // modify heartbeat state
    //

    private synchronized void onHeartbeatSent(int heartbeatId)
    {
        lastSentHeartbeatId = heartbeatId;
        unansweredHeartbeatCount++;
    }

    private synchronized void onHeartbeatReceived(DID did, Channel channel, int heartbeatId,
            long elapsedMicros)
    {
        // we're only interested in resetting the counter
        // if we receive a response to the latest heartbeat sent
        // out in a timely fashion
        if (heartbeatId == lastSentHeartbeatId) {
            l.trace("{} received heartbeat {} over {} - heartbeat count reset", did, heartbeatId, TransportUtil.hexify(channel));
            unansweredHeartbeatCount = 0;
        } else {
            l.trace("{} received heartbeat {} over {}", did, heartbeatId, TransportUtil.hexify(channel));
        }
        roundTripTimes.putMicros(channel.getId(), elapsedMicros);
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
        long recvTimeNanos = System.nanoTime();

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

        Channel channel = ctx.getChannel();
        DID did = TransportUtil.getChannelData(channel).getRemoteDID();
        int heartbeatId = msg.getHeader().getHeartbeat().getHeartbeatId();
        long sentTimeNanos = msg.getHeader().getHeartbeat().getSentTime();

        if (type == Type.HEARTBEAT_CALL) {
            // a remote peer wants to check if the connection is live
            PBTPHeader reply = PBTPHeader
                    .newBuilder()
                    .setType(Type.HEARTBEAT_REPLY)
                    .setHeartbeat(PBHeartbeat
                            .newBuilder()
                            .setHeartbeatId(heartbeatId)
                            .setSentTime(sentTimeNanos))
                    .build();
            write(ctx, future(ctx.getChannel()), TransportProtocolUtil.newControl(reply));
            l.trace("{} respond to heartbeat {} over {}", did, heartbeatId, TransportUtil.hexify(channel));
        } else {
            // we've received a heartbeat reply
            onHeartbeatReceived(did, channel, heartbeatId, (recvTimeNanos - sentTimeNanos) / 1000);
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
        final DID did = TransportUtil.getChannelData(channel).getRemoteDID();

        if (channel.getCloseFuture().isDone()) {
            l.info("{} channel {} closed - skip heartbeat reschedule", did, TransportUtil.hexify(channel));
            return;
        }

        l.trace("{} schedule heartbeat over {} after {}ms", did, TransportUtil.hexify(channel), interval);

        heartbeatTimer.newTimeout(new TimerTask()
        {
            @Override
            public void run(Timeout timeout)
                    throws Exception
            {
                int unansweredHeartbeats = getUnansweredHeartbeatCount();

                if (unansweredHeartbeats >= maxFailedHeartbeats) {
                    Channels.fireExceptionCaughtLater(channel, new ExHeartbeatTimedOut("fail " + maxFailedHeartbeats + " consecutive heartbeats"));
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
                int heartbeatId = random.nextInt(Integer.MAX_VALUE);

                PBTPHeader heartbeat = PBTPHeader
                        .newBuilder()
                        .setType(Type.HEARTBEAT_CALL)
                        .setHeartbeat(PBHeartbeat
                                .newBuilder()
                                .setHeartbeatId(heartbeatId)
                                .setSentTime(System.nanoTime()))
                        .build();

                write(ctx, future(channel), TransportProtocolUtil.newControl(heartbeat));

                onHeartbeatSent(heartbeatId);

                l.trace("{} send heartbeat {} over {}", did, heartbeatId, TransportUtil.hexify(channel));
            }
        }, interval, TimeUnit.MILLISECONDS);
    }

    private static boolean isHeartbeatMessage(Type type)
    {
        return type == Type.HEARTBEAT_CALL || type == Type.HEARTBEAT_REPLY;
    }
}
