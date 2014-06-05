package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.aerofs.daemon.transport.lib.TransportUtil;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.zephyr.client.exceptions.ExHandshakeFailed;
import com.aerofs.zephyr.client.exceptions.ExHandshakeRenegotiation;
import com.aerofs.zephyr.client.handlers.ZephyrProtocolHandler;
import com.aerofs.zephyr.proto.Zephyr.ZephyrHandshake;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;

import static com.aerofs.daemon.transport.zephyr.ZephyrClientPipelineFactory.getCNameVerifiedHandler;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

final class ZephyrClientHandler extends SimpleChannelHandler
{
    private static final Logger l = Loggers.getLogger(ZephyrClientHandler.class);

    private final IUnicastListener unicastListener;
    private final IOStatsHandler ioStatsHandler;
    private final ZephyrProtocolHandler zephyrProtocolHandler;

    // set both these fields _once_ _before_ channel is connected!
    private DID remotedid;
    private Channel channel;

    /**
     * @param zephyrProtocolHandler this is the instance of {@link ZephyrProtocolHandler} used in this pipeline
     */
    ZephyrClientHandler(IUnicastListener unicastListener, IOStatsHandler ioStatsHandler, ZephyrProtocolHandler zephyrProtocolHandler)
    {
        this.unicastListener = unicastListener;
        this.ioStatsHandler = ioStatsHandler;
        this.zephyrProtocolHandler = zephyrProtocolHandler;
    }

    void init(DID did, Channel ourChannel)
    {
        // set our internal channel
        checkState(channel == null, "attempt reset channel old:" + channel);
        channel = ourChannel;

        // this is the DID we expect the remote peer to have
        checkState(remotedid == null, "attempt reset remote did old:" + remotedid);
        getCNameVerifiedHandler(ourChannel).setExpectedRemoteDID(did);
        remotedid = did;
    }

    DID getExpectedRemoteDID()
    {
        checkValid();

        return remotedid;
    }

    boolean isClosed()
    {
        checkValid();

        return channel.getCloseFuture().isDone();
    }

    long getLocalZid()
    {
        checkValid();

        return zephyrProtocolHandler.getLocalZid();
    }

    long getRemoteZid()
    {
        checkValid();

        return zephyrProtocolHandler.getRemoteZid();
    }

    long getBytesSent()
    {
        checkValid();

        return ioStatsHandler.getBytesSentOnChannel();
    }

    long getChannelLifetime()
    {
        checkValid();

        return System.currentTimeMillis() - ioStatsHandler.getChannelCreationTime();
    }

    long getBytesReceived()
    {
        checkValid();

        return ioStatsHandler.getBytesReceivedOnChannel();
    }

    boolean hasHandshakeCompleted()
    {
        checkValid();

        return zephyrProtocolHandler.hasHandshakeCompleted();
    }

    void consumeHandshake(ZephyrHandshake handshake)
            throws ExHandshakeRenegotiation, ExHandshakeFailed
    {
        checkValid();

        // we're getting a handshake message after the handshake has succeeded
        // this means that for some reason the remote device is trying to establish a
        // connection to us again.
        if (zephyrProtocolHandler.hasHandshakeCompleted()) {
            throw new ExHandshakeRenegotiation("attempted to renegotiate zephyr handshake on incoming signalling");
        }

        zephyrProtocolHandler.processIncomingZephyrSignallingMessage(handshake);
    }

    void disconnect(Exception cause)
    {
        checkValid();

        ZephyrClientPipelineFactory.getMessageHandler(channel).setDisconnectReason(cause);
        channel.close();
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        checkValid();

        if (TransportUtil.isChannelConnected(e.getChannel())) {
            l.info("{} channel {} closed - notify listener", remotedid, TransportUtil.hexify(channel));
            unicastListener.onDeviceDisconnected(remotedid);
        }

        super.channelClosed(ctx, e);
    }

    private void checkValid()
    {
        checkNotNull(remotedid);
        checkNotNull(channel);
    }
}
