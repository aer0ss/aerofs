package com.aerofs.zephyr.client.pipeline;

import com.aerofs.base.Loggers;
import com.aerofs.zephyr.client.IZephyrSignallingClient;
import com.aerofs.zephyr.client.IZephyrSignallingService;
import com.aerofs.zephyr.client.ZephyrHandshakeEngine;
import com.aerofs.zephyr.client.ZephyrHandshakeEngine.HandshakeReturn;
import com.aerofs.zephyr.client.exception.ExHandshakeFailed;
import com.aerofs.zephyr.client.message.BindRequest;
import com.aerofs.zephyr.client.message.Registration;
import com.aerofs.zephyr.proto.Zephyr.ZephyrControlMessage;
import com.aerofs.zephyr.proto.Zephyr.ZephyrHandshake;
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
import java.util.concurrent.TimeUnit;

import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_INVALID_CHAN_ID;
import static com.aerofs.zephyr.client.ZephyrHandshakeEngine.HandshakeReturn.NO_ACTION;
import static com.aerofs.zephyr.client.ZephyrHandshakeEngine.HandshakeState.SUCCEEDED;
import static com.aerofs.zephyr.proto.Zephyr.ZephyrControlMessage.Type.HANDSHAKE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.jboss.netty.channel.Channels.connect;
import static org.jboss.netty.channel.Channels.fireChannelConnected;
import static org.jboss.netty.channel.Channels.fireExceptionCaught;
import static org.jboss.netty.channel.Channels.future;
import static org.jboss.netty.channel.Channels.write;

// FIXME (AG): don't deal with PBs here if I can avoid it
// FIXME (AG): avoid synchronizing every method
// it's possible for me to avoid synchronization by using channels.fireMessageReceived with a Handshake object
final class ZephyrHandshakeHandler extends SimpleChannelHandler implements IZephyrSignallingClient
{
    private static final Logger l = Loggers.getLogger(ZephyrHandshakeHandler.class);

    private final IZephyrSignallingService signallingService;
    private final Timer handshakeTimer;
    private final long handshakeTimeout;
    private final TimeUnit handshakeTimeoutTimeUnit;
    private final ZephyrHandshakeEngine handshakeEngine = new ZephyrHandshakeEngine();

    private @Nullable ChannelStateEvent originalConnectEvent;
    private @Nullable ChannelHandlerContext handlerCtx;

    ZephyrHandshakeHandler(IZephyrSignallingService signallingService, Timer handshakeTimer, long handshakeTimeout, TimeUnit handshakeTimeoutTimeUnit)
    {
        this.signallingService = signallingService;
        this.handshakeTimer = handshakeTimer;
        this.handshakeTimeout = handshakeTimeout;
        this.handshakeTimeoutTimeUnit = handshakeTimeoutTimeUnit;
    }

    @Override
    public synchronized void connectRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        l.debug("c:{} begin connect", getChannelId(e.getChannel()));

        Channel channel = e.getChannel();

        checkState(originalConnectEvent == null, "connect called multiple times  for " + getChannelId(channel));
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
        throw new IllegalStateException("cannot write before zephyr handshake complete");
    }

    @Override
    public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (!(e.getMessage() instanceof Registration)) {
            super.messageReceived(ctx, e);
            return;
        }

        Registration reg = (Registration) e.getMessage();
        handshakeEngine.setLocalZid(reg.getAssignedZid());
        handshake(ctx);
    }

    private void handshake(ChannelHandlerContext ctx)
            throws ExHandshakeFailed
    {
        l.debug("c:{} begin zephyr handshake", getChannelId(ctx.getChannel()));

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
                if (!(handshakeEngine.getState() == SUCCEEDED)) {
                    fireExceptionCaught(channel, new ExHandshakeFailed("timeout during handshake for " + getChannelId(channel)));
                }
            }
        }, handshakeTimeout, handshakeTimeoutTimeUnit);
    }

    @Override
    public synchronized void processIncomingZephyrSignallingMessage(ZephyrHandshake incoming) // IMPORTANT: called on non-IO thread!
            throws ExHandshakeFailed
    {

        l.debug("c:{} <-sig ms:{} md:{}", getChannelId(checkNotNull(handlerCtx).getChannel()), incoming.getSourceZephyrId(), incoming.getDestinationZephyrId());

        checkNotNull(handlerCtx);

        if (handlerCtx.getChannel().getCloseFuture().isDone()) {
            l.warn("c:{} <-sig after close", getChannelId(handlerCtx.getChannel()));
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
            l.debug("c:{} handshake engine returned NO_ACTION", getChannelId(channel));
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

        l.debug("c:{} ->sig ha:{} ms:{} md:{}", getChannelId(channel), action, handshake.getSourceZephyrId(), handshake.getDestinationZephyrId());

        ZephyrControlMessage outgoing = ZephyrControlMessage
                .newBuilder()
                .setType(HANDSHAKE)
                .setHandshake(handshake)
                .build();

        signallingService.sendZephyrSignallingMessage(channel, outgoing.toByteArray());
    }

    private void bind(final ChannelHandlerContext ctx, int remoteZid)
    {
        l.debug("c:{} bind to remote l:{} r:{}", getChannelId(ctx.getChannel()), handshakeEngine.getLocalZid(), remoteZid);

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

                    l.debug("c:{} succeed bind write", getChannelId(channel));
                    originalConnectEvent.getFuture().setSuccess();
                    fireChannelConnected(ctx, (SocketAddress) originalConnectEvent.getValue());
                } else {
                    l.debug("c:{} fail bind write", getChannelId(channel));
                    Throwable originalCause = channelFuture.getCause();
                    fireExceptionCaught(ctx, originalCause == null ? new IOException("bind write failed") : originalCause);
                }
            }
        });

        write(ctx, writeFuture, new BindRequest(remoteZid));
    }

    private Integer getChannelId(Channel channel)
    {
        return channel.getId(); // FIXME (AG): cache
    }
}
