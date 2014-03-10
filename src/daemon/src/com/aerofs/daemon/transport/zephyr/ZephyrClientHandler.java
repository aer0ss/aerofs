package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.lib.ChannelDataUtil;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.aerofs.zephyr.client.exceptions.ExHandshakeFailed;
import com.aerofs.zephyr.client.exceptions.ExHandshakeRenegotiation;
import com.aerofs.zephyr.client.handlers.ZephyrProtocolHandler;
import com.aerofs.zephyr.proto.Zephyr.ZephyrHandshake;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

final class ZephyrClientHandler extends SimpleChannelHandler
{
    private static final Logger l = Loggers.getLogger(ZephyrClientHandler.class);

    private final IUnicastListener unicastListener;
    private final ZephyrProtocolHandler zephyrProtocolHandler;

    // set both these fields _once_ _before_ channel is connected!
    private DID remotedid;
    private Channel channel;

    /**
     * @param zephyrProtocolHandler this is the instance of {@link ZephyrProtocolHandler} used in this pipeline
     */
    ZephyrClientHandler(IUnicastListener unicastListener, ZephyrProtocolHandler zephyrProtocolHandler)
    {
        this.unicastListener = unicastListener;
        this.zephyrProtocolHandler = zephyrProtocolHandler;
    }

    void init(DID did, Channel ourChannel)
    {
        checkState(remotedid == null, "attempt reset remote did old:" + remotedid);
        remotedid = did;

        checkState(channel == null, "attempt reset channel old:" + channel);
        channel = ourChannel;
    }

    DID getExpectedRemoteDID()
    {
        checkValid();

        return remotedid;
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

        if (ChannelDataUtil.isChannelConnected(e.getChannel())) {
            l.info("{} channel closed - notify listener", this);
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
