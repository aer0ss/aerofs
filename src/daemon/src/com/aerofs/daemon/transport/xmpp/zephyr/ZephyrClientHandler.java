package com.aerofs.daemon.transport.xmpp.zephyr;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.CNameVerificationHandler.CNameListener;
import com.aerofs.daemon.transport.lib.IConnectionServiceListener;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.zephyr.client.IZephyrSignallingClient;
import com.aerofs.zephyr.client.exceptions.ExBadZephyrMessage;
import com.aerofs.zephyr.client.exceptions.ExHandshakeFailed;
import com.aerofs.zephyr.client.exceptions.ExHandshakeRenegotiation;
import com.aerofs.zephyr.client.handlers.ZephyrProtocolHandler;
import com.aerofs.zephyr.proto.Zephyr.ZephyrHandshake;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicReference;

import static com.aerofs.base.net.ChannelUtil.pretty;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

final class ZephyrClientHandler extends SimpleChannelHandler implements CNameListener
{
    private static final Logger l = Loggers.getLogger(ZephyrClientHandler.class);

    private final DID localdid;
    private final ZephyrConnectionService connectionService;
    private final IConnectionServiceListener connectionServiceListener;
    private final ZephyrProtocolHandler zephyrProtocolHandler;
    private final AtomicReference<UserID> remoteid = new AtomicReference<UserID>(null); // set when cname verification completes
    private final AtomicReference<Throwable> disconnectCause = new AtomicReference<Throwable>(null); // set once, to the first exception thrown, or first disconnection reason

    // IMPORTANT: set _once_ _before_ the channel is connected!

    private DID remotedid;
    private String channelId;
    private Channel channel;
    private IOStatsHandler iostatsHandler;

    /**
     * @param zephyrProtocolHandler this is the instance of {@link ZephyrProtocolHandler} used in this pipeline
     */
    ZephyrClientHandler(DID localdid, ZephyrConnectionService connectionService, IConnectionServiceListener connectionServiceListener, ZephyrProtocolHandler zephyrProtocolHandler)
    {
        this.localdid = localdid;
        this.connectionService = connectionService;
        this.connectionServiceListener = connectionServiceListener;
        this.zephyrProtocolHandler = zephyrProtocolHandler;
    }

    void init(DID did, Channel ourChannel)
    {
        checkState(remotedid == null, "attempt reset remote did old:" + remotedid);
        remotedid = did;

        checkState(channel == null, "attempt reset channel old:" + channel);
        channel = ourChannel;

        checkState(channelId == null, "attempt reset channel id old:" + channelId);
        channelId = pretty(ourChannel);

        checkState(iostatsHandler == null);
        iostatsHandler = ourChannel.getPipeline().get(IOStatsHandler.class);
    }

    DID getRemote()
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

    void disconnect(Throwable cause)
    {
        checkValid();

        boolean succeeded = disconnectCause.compareAndSet(null, cause);
        if (succeeded) {
            l.debug("{} close channel cause:{}", this, cause);

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
        connectionServiceListener.onDeviceConnected(remotedid, connectionService);

        super.channelConnected(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (!(e.getMessage() instanceof ChannelBuffer)) {
            throw new ExBadZephyrMessage("wrong message recvd:" + e.getMessage().getClass().getSimpleName() + " for " + remotedid);
        }

        checkValid();

        ChannelBufferInputStream is = new ChannelBufferInputStream((ChannelBuffer) e.getMessage());
        connectionServiceListener.onIncomingMessage(remotedid, remoteid.get(), is, is.available());
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        checkNotNull(remoteid, "cname verification not completed");

        super.writeRequested(ctx, e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception
    {
        checkValid();

        l.warn("{} caught err:", this, e.getCause());

        disconnect(e.getCause());
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        checkValid();

        if (remoteid.get() != null) { // we've connected
            l.info("{} channel closed - notify listener", this);
            connectionServiceListener.onDeviceDisconnected(remotedid, connectionService);
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
        IZephyrSignallingClient signallingClient = zephyrProtocolHandler.getZephyrSignallingClient();

        // we're getting a handshake message after the handshake has succeeded
        // this means that for some reason the remote device is trying to establish a
        // connection to us again.
        if (signallingClient == null) {
            throw new ExHandshakeRenegotiation("attempted to renegotiate zephyr handshake on incoming signalling");
        }

        signallingClient.processIncomingZephyrSignallingMessage(handshake);
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
}
