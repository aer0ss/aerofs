/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.zephyr.client.handlers.ZephyrProtocolHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.ssl.SslHandler;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static com.google.common.base.Preconditions.checkState;

/**
 * This class exists to create an {@link org.jboss.netty.handler.ssl.SslHandler} in either server
 * or client mode depending on the outcome of the zephyr handshake.
 */
final class StandInSslHandler extends SimpleChannelHandler
{
    private final SSLEngineFactory clientSslEngineFactory;
    private final SSLEngineFactory serverSslEngineFactory;
    private final ZephyrProtocolHandler zephyrProtocolHandler;

    /**
     * @param zephyrProtocolHandler instance of {@link ZephyrProtocolHandler used in this pipeline}
     */
    StandInSslHandler(SSLEngineFactory clientSslEngineFactory, SSLEngineFactory serverSslEngineFactory, ZephyrProtocolHandler zephyrProtocolHandler)
    {
        this.clientSslEngineFactory = clientSslEngineFactory;
        this.serverSslEngineFactory = serverSslEngineFactory;
        this.zephyrProtocolHandler = zephyrProtocolHandler;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        // ok - first find the ZephyrProtocolHandler and check what mode
        // we should construct the SslHandler in (the handler must exist and the
        // handshake _must_ have completed)

        checkState(zephyrProtocolHandler.hasHandshakeCompleted());

        Mode sslMode;
        if (zephyrProtocolHandler.getLocalZid() < zephyrProtocolHandler.getRemoteZid()) {
            sslMode = Mode.Server; // we connected to the server first, we get to be the server
        } else {
            sslMode = Mode.Client;
        }

        SslHandler sslHandler = newSslHandler(sslMode);

        ctx.getPipeline().addAfter(ctx.getName(), "ssl", sslHandler);

        super.channelConnected(ctx, e);
    }

    private SslHandler newSslHandler(Mode mode)
            throws IOException, GeneralSecurityException
    {
        SslHandler handler;
        if (mode.equals(Mode.Server)) {
            handler = new SslHandler(serverSslEngineFactory.getSSLEngine());
        } else {
            handler = new SslHandler(clientSslEngineFactory.getSSLEngine());
        }

        handler.setIssueHandshake(false);
        handler.setCloseOnSSLException(true);
        handler.setEnableRenegotiation(false);

        return handler;
    }
}
