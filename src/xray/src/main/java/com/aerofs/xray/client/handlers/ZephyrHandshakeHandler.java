package com.aerofs.xray.client.handlers;

import com.aerofs.base.Loggers;
import com.aerofs.xray.client.IZephyrSignallingClient;
import com.aerofs.xray.client.IZephyrSignallingService;
import com.aerofs.xray.client.ZephyrHandshakeEngine;
import com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeReturn;
import com.aerofs.xray.client.exceptions.ExHandshakeFailed;
import com.aerofs.xray.proto.XRay.ZephyrControlMessage;
import com.aerofs.xray.proto.XRay.ZephyrHandshake;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static com.aerofs.base.net.ChannelUtil.pretty;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_BIND_MSG_LEN;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_BIND_PAYLOAD_LEN;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_INVALID_CHAN_ID;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_MAGIC;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_MSG_BYTE_ORDER;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeReturn.NO_ACTION;
import static com.aerofs.xray.client.ZephyrHandshakeEngine.HandshakeState.SUCCEEDED;
import static com.aerofs.xray.proto.XRay.ZephyrControlMessage.Type.HANDSHAKE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayListWithCapacity;
import static org.jboss.netty.buffer.ChannelBuffers.buffer;
import static org.jboss.netty.channel.Channels.connect;
import static org.jboss.netty.channel.Channels.fireChannelConnected;
import static org.jboss.netty.channel.Channels.fireExceptionCaught;
import static org.jboss.netty.channel.Channels.fireMessageReceived;
import static org.jboss.netty.channel.Channels.future;
import static org.jboss.netty.channel.Channels.write;

// FIXME (AG): don't deal with PBs here if I can avoid it
// FIXME (AG): avoid synchronizing every method
final class ZephyrHandshakeHandler extends SimpleChannelHandler implements IZephyrSignallingClient
{
    private static final Logger l = Loggers.getLogger(ZephyrHandshakeHandler.class);

    private final IZephyrSignallingService signallingService;
    private final Timer handshakeTimer;
    private final long handshakeTimeout;
    private final TimeUnit handshakeTimeoutTimeUnit;
    private final ZephyrHandshakeEngine handshakeEngine = new ZephyrHandshakeEngine();
    private final ArrayList<ChannelBuffer> receivedBuffers = newArrayListWithCapacity(100);

    private volatile String channelId;

    private @Nullable ChannelStateEvent originalConnectEvent;
    private @Nullable ChannelHandlerContext handlerCtx;

    ZephyrHandshakeHandler(IZephyrSignallingService signallingService, Timer handshakeTimer, long handshakeTimeout, TimeUnit handshakeTimeoutTimeUnit)
    {
        this.signallingService = signallingService;
        this.handshakeTimer = handshakeTimer;
        this.handshakeTimeout = handshakeTimeout;
        this.handshakeTimeoutTimeUnit = handshakeTimeoutTimeUnit;
    }

    synchronized long getRemoteZid()
    {
        return handshakeEngine.getRemoteZid();
    }

    synchronized long getLocalZid()
    {
        return handshakeEngine.getLocalZid();
    }

    synchronized boolean hasHandshakeCompleted()
    {
        return handshakeEngine.getState() == SUCCEEDED;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        channelId = pretty(e.getChannel());

        super.channelOpen(ctx, e);
    }

    @Override
    public synchronized void connectRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        Channel channel = e.getChannel();

        checkState(originalConnectEvent == null, "c:"  + channelId + " connect called multiple times");
        originalConnectEvent = e;

        checkState(handlerCtx == null, "ctx already set old:" + handlerCtx);
        handlerCtx = ctx;

        ChannelFuture passedOnFuture = future(channel);
        channel.getCloseFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture channelFuture)
                    throws Exception
            {
                originalConnectEvent.getFuture().setFailure(new IOException("channel closed during zephyr handshake"));
            }
        });

        passedOnFuture.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture channelFuture)
                    throws Exception
            {
                if (!channelFuture.isSuccess()) {
                    Throwable originalCause = channelFuture.getCause();
                    originalConnectEvent.getFuture().setFailure(originalCause == null ? new IOException("connect failed during zephyr handshake") : originalCause);
                }
            }
        });

        connect(ctx, passedOnFuture, (SocketAddress) e.getValue());
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        // drop this to the floor - we have to wait until binding is completed
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        throw new IllegalStateException("zephyr handshake not completed");
    }

    @Override
    public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        Object incoming = e.getMessage();

        if (incoming instanceof Registration) {
            l.debug("c:{} received reg", channelId);

            Registration reg = (Registration) incoming;
            handshakeEngine.setLocalZid(reg.getAssignedZid());
            handshake(ctx);
        } else if (incoming instanceof ChannelBuffer) {
            l.debug("c:{} buffer incoming buffer size:{}", channelId, receivedBuffers.size());

            ChannelBuffer buf = (ChannelBuffer) incoming;
            boolean added = receivedBuffers.add(buf);
            checkState(added);
        } else {
            throw new IllegalArgumentException("unexpected type:" + incoming.getClass().getSimpleName());
        }
    }

    private void handshake(ChannelHandlerContext ctx)
            throws ExHandshakeFailed
    {
        l.trace("c:{} begin zephyr handshake", channelId);

        setHandshakeTimeout(ctx.getChannel());
        HandshakeReturn action = handshakeEngine.startHandshaking();
        handleHandshakeAction(ctx.getChannel(), action);
    }

    private void setHandshakeTimeout(final Channel channel)
    {
        handshakeTimer.newTimeout(new TimerTask()
        {
            @Override
            public void run(Timeout timeout)
                    throws Exception
            {
                if (!channel.getCloseFuture().isDone() && !(handshakeEngine.getState() == SUCCEEDED)) {
                    fireExceptionCaught(channel, new ExHandshakeFailed("timeout during handshake for " + channelId));
                }
            }
        }, handshakeTimeout, handshakeTimeoutTimeUnit);
    }

    @Override
    public synchronized void processIncomingZephyrSignallingMessage(ZephyrHandshake incoming) // IMPORTANT: called on non-IO thread!
            throws ExHandshakeFailed
    {
        l.debug("c:{} <-sig ms:{} md:{}", channelId, incoming.getSourceZephyrId(), incoming.getDestinationZephyrId());

        checkNotNull(handlerCtx);

        if (handlerCtx.getChannel().getCloseFuture().isDone()) {
            l.warn("c:{} <-sig after close", channelId);
            return;
        }

        HandshakeReturn action = handshakeEngine.consume(incoming);

        if (handshakeEngine.getLocalZid() != ZEPHYR_INVALID_CHAN_ID) { // we've registered
            handleHandshakeAction(handlerCtx.getChannel(), action);
        }

        if (handshakeEngine.getState() == SUCCEEDED) {
            bind(handlerCtx, handshakeEngine.getRemoteZid());
        }
    }

    private void handleHandshakeAction(Channel channel, HandshakeReturn action)
            throws ExHandshakeFailed
    {
        if (action == NO_ACTION) {
            l.trace("c:{} handshake engine returned NO_ACTION", channelId);
            return;
        }

        ZephyrHandshake handshake;

        switch (action) {
        case SEND_SYN:
            handshake = handshakeEngine.newSyn();
            break;
        case SEND_SYNACK:
            handshake = handshakeEngine.newSynAck();
            break;
        case SEND_ACK:
            handshake = handshakeEngine.newAck();
            break;
        default:
            throw new ExHandshakeFailed("unexpected handshake action:" + action);
        }

        l.debug("c:{} ->sig ha:{} ms:{} md:{}", channelId, action, handshake.getSourceZephyrId(), handshake.getDestinationZephyrId());

        ZephyrControlMessage outgoing = ZephyrControlMessage
                .newBuilder()
                .setType(HANDSHAKE)
                .setHandshake(handshake)
                .build();

        signallingService.sendZephyrSignallingMessage(channel, outgoing.toByteArray());
    }

    private void bind(final ChannelHandlerContext ctx, int remoteZid)
    {
        l.debug("c:{} bind to remote l:{} r:{}", channelId, handshakeEngine.getLocalZid(), remoteZid);

        ChannelFuture writeFuture = future(ctx.getChannel());
        writeFuture.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture channelFuture)
                    throws Exception
            {
                Channel channel = channelFuture.getChannel();
                channel.getPipeline().remove(ZephyrHandshakeHandler.this);

                if (channelFuture.isSuccess()) {
                    checkNotNull(originalConnectEvent);

                    l.debug("c:{} succeed bind write", channelId);
                    originalConnectEvent.getFuture().setSuccess();
                    fireChannelConnected(ctx, (SocketAddress) originalConnectEvent.getValue());

                    drainReceived(ctx);
                } else {
                    l.warn("c:{} fail bind write", channelId);
                    Throwable originalCause = channelFuture.getCause();
                    fireExceptionCaught(ctx, originalCause == null ? new IOException("bind write failed") : originalCause);
                }
            }
        });

        write(ctx, writeFuture, newZephyrBindMessage(remoteZid));
    }

    private ChannelBuffer newZephyrBindMessage(int remoteZid)
    {
        ChannelBuffer protocolBuffer = buffer(ZEPHYR_BIND_MSG_LEN);

        checkState(protocolBuffer.order() == ZEPHYR_MSG_BYTE_ORDER, "bad byteorder exp:" + ZEPHYR_MSG_BYTE_ORDER + " act:" + protocolBuffer.order());
        protocolBuffer.writeBytes(ZEPHYR_MAGIC);
        protocolBuffer.writeInt(ZEPHYR_BIND_PAYLOAD_LEN);
        protocolBuffer.writeInt(remoteZid);

        return protocolBuffer;
    }

    private void drainReceived(ChannelHandlerContext ctx)
    {
        Iterator<ChannelBuffer> it = receivedBuffers.iterator();
        while (it.hasNext()) {
            ChannelBuffer buffer = it.next();
            it.remove();
            fireMessageReceived(ctx, buffer);
        }
    }
}
