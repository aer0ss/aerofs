package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.CNameVerificationHandler.CNameListener;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelDIDProvider;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportMessage;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.zephyr.client.exceptions.ExBadZephyrMessage;
import com.aerofs.zephyr.client.exceptions.ExHandshakeFailed;
import com.aerofs.zephyr.client.exceptions.ExHandshakeRenegotiation;
import com.aerofs.zephyr.client.handlers.ZephyrProtocolHandler;
import com.aerofs.zephyr.proto.Zephyr.ZephyrHandshake;
import com.google.common.collect.Queues;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import static com.aerofs.base.net.ChannelUtil.pretty;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.jboss.netty.channel.Channels.fireMessageReceived;
import static org.jboss.netty.channel.Channels.write;

final class ZephyrClientHandler extends SimpleChannelHandler implements CNameListener, ChannelDIDProvider
{
    private static final Logger l = Loggers.getLogger(ZephyrClientHandler.class);

    private final DID localdid;
    private final IUnicastListener unicastListener;
    private final ZephyrProtocolHandler zephyrProtocolHandler;
    private final AtomicReference<UserID> remoteid = new AtomicReference<UserID>(null); // set when cname verification completes
    private final AtomicReference<Throwable> disconnectCause = new AtomicReference<Throwable>(null); // set once, to the first exception thrown, or first disconnection reason

    // IMPORTANT: set _once_ _before_ the channel is connected!

    private DID remotedid;
    private String channelId;
    private Channel channel;
    private IOStatsHandler iostatsHandler;

    // write-related

    private final Queue<PendingWrite> pendingWrites = Queues.newConcurrentLinkedQueue();
    private final Object writeLock = new Object();

    /**
     * @param zephyrProtocolHandler this is the instance of {@link ZephyrProtocolHandler} used in this pipeline
     */
    ZephyrClientHandler(DID localdid, IUnicastListener unicastListener, ZephyrProtocolHandler zephyrProtocolHandler)
    {
        this.localdid = localdid;
        this.unicastListener = unicastListener;
        this.zephyrProtocolHandler = zephyrProtocolHandler;
    }

    void init(DID did, Channel ourChannel)
    {
        // set our internal state

        checkState(remotedid == null, "attempt reset remote did old:" + remotedid);
        remotedid = did;

        checkState(channel == null, "attempt reset channel old:" + channel);
        channel = ourChannel;

        checkState(channelId == null, "attempt reset channel id old:" + channelId);
        channelId = pretty(ourChannel);

        checkState(iostatsHandler == null);
        iostatsHandler = ourChannel.getPipeline().get(IOStatsHandler.class);

        // fail any pending writes when the channel dies

        channel.getCloseFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture channelFuture)
                    throws Exception
            {
                failPendingWrites(getDisconnectCause());
            }
        });
    }

    @Override
    public DID getRemoteDID()
    {
        checkValid();

        return remotedid;
    }

    long getBytesReceived()
    {
        checkValid();

        return iostatsHandler.getBytesReceivedOnChannel();
    }

    boolean hasHandshakeCompleted()
    {
        checkValid();

        return zephyrProtocolHandler.hasHandshakeCompleted();
    }

    boolean isConnected()
    {
        return remoteid.get() != null;
    }

    private Throwable getDisconnectCause()
    {
        // noinspection ThrowableResultOfMethodCallIgnored
        return disconnectCause.get() == null ? new ExDeviceUnavailable("unreachable") : disconnectCause.get();
    }

    void disconnect(Throwable cause)
    {
        checkValid();

        boolean succeeded = disconnectCause.compareAndSet(null, cause);
        if (succeeded) {
            l.debug("{} close channel cause:", this, cause);

            // FIXME (AG): this happens when you throw an exception
            // handlers will call close first, which triggers the close future, followed by the exception event

            if (channel.getCloseFuture().isDone()) {
                l.warn("{} cause was unset, but channel already closed", this);
                return;
            }

            channel.close();
        }
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        checkValid();

        checkState(!ctx.getChannel().getCloseFuture().isDone());
        unicastListener.onDeviceConnected(remotedid);
        sendPendingWrites();

        super.channelConnected(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (!(e.getMessage() instanceof ChannelBuffer)) {
            throw new ExBadZephyrMessage("recv bad msg:" + e.getMessage().getClass().getSimpleName() + " for " + remotedid);
        }

        checkValid();

        fireMessageReceived(ctx, new TransportMessage((ChannelBuffer)e.getMessage(), remotedid, remoteid.get()));
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (!(e.getMessage() instanceof ChannelBuffer)) {
            throw new ExBadZephyrMessage("send bad msg:" + e.getMessage().getClass().getSimpleName() + " for " + remotedid);
        }

        ChannelBuffer buffer = (ChannelBuffer) e.getMessage();
        ChannelFuture writeFuture = e.getFuture();

        synchronized (writeLock) {
            if (channel.getCloseFuture().isDone()) {
                writeFuture.setFailure(getDisconnectCause());
            } else if (isConnected()) {
                doWrite(ctx, buffer, writeFuture);
            } else {
                pendingWrites.add(new PendingWrite(ctx, buffer, writeFuture));
            }
        }
    }

    private void doWrite(ChannelHandlerContext ctx, ChannelBuffer buffer, ChannelFuture writeFuture)
    {
        final int length = buffer.readableBytes();
        writeFuture.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture writeFuture)
                    throws Exception
            {
                if (writeFuture.isSuccess()) {
                    l.trace("d:{} wrote {} bytes", remotedid, length);
                } else {
                    l.warn("d:{} fail write {}", remotedid, length);
                }
            }
        });

        write(ctx, writeFuture, buffer);
    }

    private void sendPendingWrites()
    {
        // We need to synchronize on writeLock to ensure proper ordering of writes,
        // otherwise another thread could write in the middle of the pending writes.
        synchronized (writeLock) {
            PendingWrite pending;
            while ((pending = pendingWrites.poll()) != null) {
                doWrite(pending.ctx, pending.buffer, pending.writeFuture);
            }
        }
    }

    private void failPendingWrites(Throwable reason)
    {
        synchronized (writeLock) { // IMPORTANT: may be called while we are iterating over pendingWrites inside sendPendingWrites()
            PendingWrite pending;
            while ((pending = pendingWrites.poll()) != null) {
                pending.writeFuture.setFailure(reason);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception
    {
        checkValid();

        l.warn("{} caught err:", this, LogUtil.suppress(e.getCause(), ExHandshakeFailed.class));

        disconnect(e.getCause());
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        checkValid();

        if (isConnected()) {
            l.info("{} channel closed - notify listener", this);
            unicastListener.onDeviceDisconnected(remotedid);
        }

        super.channelClosed(ctx, e);
    }

    @Override
    public void onPeerVerified(UserID user, DID did)
    {
        checkValid();

        if (channel.getCloseFuture().isDone()) {
            l.warn("{} cname verification complete but channel closed", this);
            return;
        }

        boolean succeeded = remoteid.compareAndSet(null, user);
        if (!succeeded) {
            throw new IllegalStateException("user id already set old:" + remoteid.get() + " new:" + user);
        }

        l.info("{} connected for duplex tx zc:{}", this, debugString());
    }

    public void consumeHandshake(ZephyrHandshake handshake)
            throws ExHandshakeRenegotiation, ExHandshakeFailed
    {
        // we're getting a handshake message after the handshake has succeeded
        // this means that for some reason the remote device is trying to establish a
        // connection to us again.
        if (zephyrProtocolHandler.hasHandshakeCompleted()) {
            throw new ExHandshakeRenegotiation("attempted to renegotiate zephyr handshake on incoming signalling");
        }

        zephyrProtocolHandler.processIncomingZephyrSignallingMessage(handshake);
    }

    @Override
    public String toString()
    {
        return String.format("d:%s c:%s", remotedid, channelId);
    }

    String debugString()
    {
        return String.format("[(%s) %s -> %s : tx=%d rx=%d c=%s dc=%s]",
                channelId,
                localdid,
                remotedid,
                iostatsHandler.getBytesSentOnChannel(),
                iostatsHandler.getBytesReceivedOnChannel(),
                channel.getCloseFuture().isDone(),
                disconnectCause);
    }

    private void checkValid()
    {
        checkNotNull(remotedid);
        checkNotNull(channel);
        checkNotNull(channelId);
        checkNotNull(iostatsHandler);
    }

    //
    // types
    //

    private static class PendingWrite
    {
        final ChannelHandlerContext ctx;
        final ChannelBuffer buffer;
        final ChannelFuture writeFuture;

        PendingWrite(ChannelHandlerContext ctx, ChannelBuffer buffer, ChannelFuture writeFuture)
        {
            this.ctx = ctx;
            this.buffer = buffer;
            this.writeFuture = writeFuture;
        }
    }
}
