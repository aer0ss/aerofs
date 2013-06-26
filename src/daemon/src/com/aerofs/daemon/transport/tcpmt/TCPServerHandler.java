/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.MagicHeader.ExBadMagicHeader;
import com.aerofs.base.ssl.CNameVerificationHandler.CNameListener;
import com.aerofs.lib.Util;
import com.aerofs.lib.log.LogUtil;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;

class TCPServerHandler extends SimpleChannelHandler implements CNameListener
{
    static interface ITCPServerHandlerListener
    {
        /**
         * Called when we have a connected channel to receive data from a remote peer
         */
        void onIncomingChannel(DID did, Channel channel);
    }

    private static final Logger l = Loggers.getLogger(TCPServerHandler.class);

    private final ITCPServerHandlerListener _listener;
    private final ITCP _tcp;
    private volatile DID _did;
    private volatile UserID _userID;
    private Channel _channel;
    private final AtomicBoolean _disconnected = new AtomicBoolean(false);

    TCPServerHandler(ITCPServerHandlerListener listener, ITCP tcp)
    {
        _listener = listener;
        _tcp = tcp;
    }

    /**
     * Called by the CNameVerificationHandler after we successfully verified the identity of the
     * remote user
     */
    @Override
    public void onPeerVerified(UserID userID, DID did)
    {
        _did = did;
        _userID = userID;
    }

    public void disconnect()
    {
        // Note: though Netty docs says that calling close() several times on a channel is ok, we
        // observe that this actually causes problems with the SslHandler. So we make sure to only
        // call it once
        if (_disconnected.getAndSet(true)) return;

        l.info("server: disconnect from {} {}", _did, _channel);
        _channel.close();
    }

    /**
     * @return the pipeline associated with this handler
     */
    public ChannelPipeline getPipeline()
    {
        return _channel.getPipeline();
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        _channel = e.getChannel();
        super.channelOpen(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        checkNotNull(_did);
        l.info("server connected to {} {}", _did, _channel);
        _listener.onIncomingChannel(_did, e.getChannel());
        super.channelConnected(ctx, e);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        throw new IllegalStateException("can't write on server handler");
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
    {
        try {
            ChannelBufferInputStream is = new ChannelBufferInputStream((ChannelBuffer)e.getMessage());

            l.trace("recvd {} bytes from {}", is.available(), _did);

            checkNotNull(_did);

            InetAddress remote = ((InetSocketAddress)e.getRemoteAddress()).getAddress();
            _tcp.onMessageReceived(remote, _did, _userID, is);

        } catch (Exception ex) {
            l.warn("server: ex while processing msg from: {} {}", _did, Util.e(ex));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        // This is the only place where _channel can potentially be null
        if (_channel == null) _channel = e.getChannel();

        // Close the connection when an exception is raised.
        l.warn("server: caught ex from: {} {}", _did, _channel, LogUtil.suppress(e.getCause(),
                ExBadMagicHeader.class, UnresolvedAddressException.class, IOException.class,
                SSLException.class, SSLHandshakeException.class));

        disconnect();
    }
}
