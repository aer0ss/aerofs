/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.transport.lib.TransportProtocolHandler;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.netty.ClientHandler;
import com.aerofs.daemon.transport.netty.ServerHandler;
import com.aerofs.daemon.transport.netty.ServerHandler.IServerHandlerListener;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;

import static com.aerofs.daemon.transport.netty.BootstrapFactoryUtil.newCNameVerificationHandler;
import static com.aerofs.daemon.transport.netty.BootstrapFactoryUtil.newFrameDecoder;
import static com.aerofs.daemon.transport.netty.BootstrapFactoryUtil.newLengthFieldPrepender;
import static com.aerofs.daemon.transport.netty.BootstrapFactoryUtil.newMagicReader;
import static com.aerofs.daemon.transport.netty.BootstrapFactoryUtil.newMagicWriter;
import static com.aerofs.daemon.transport.netty.BootstrapFactoryUtil.newSslHandler;
import static com.aerofs.daemon.transport.netty.BootstrapFactoryUtil.newStatsHandler;

/**
 * Helper class to create client and server bootstraps
 */
class JingleBootstrapFactory
{
    private final UserID _localuser;
    private final DID _localdid;
    private final SSLEngineFactory _clientSslEngineFactory;
    private final SSLEngineFactory _serverSslEngineFactory;
    private final TransportStats _stats;

    JingleBootstrapFactory(UserID localuser, DID localdid, SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory, TransportStats stats)
    {
        _localuser = localuser;
        _localdid = localdid;
        _clientSslEngineFactory = clientSslEngineFactory;
        _serverSslEngineFactory = serverSslEngineFactory;
        _stats = stats;
    }

    ClientBootstrap newClientBootstrap(SignalThread signalThread)
    {
        ClientBootstrap bootstrap = new ClientBootstrap(new JingleClientChannelFactory(signalThread));
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                ClientHandler clientHandler = new ClientHandler();

                return Channels.pipeline(
                        newStatsHandler(_stats),
                        newSslHandler(_clientSslEngineFactory),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newMagicReader(),
                        newMagicWriter(),
                        newCNameVerificationHandler(clientHandler, _localuser, _localdid),
                        clientHandler);
            }
        });
        return bootstrap;
    }

    ServerBootstrap newServerBootstrap(SignalThread signalThread,
            final IServerHandlerListener listener, final TransportProtocolHandler protocolHandler)
    {
        ServerBootstrap bootstrap = new ServerBootstrap(new JingleServerChannelFactory(signalThread));
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                ServerHandler serverHandler = new ServerHandler(listener);

                return Channels.pipeline(
                        newStatsHandler(_stats),
                        newSslHandler(_serverSslEngineFactory),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newMagicReader(),
                        newMagicWriter(),
                        newCNameVerificationHandler(serverHandler, _localuser, _localdid),
                        serverHandler,
                        protocolHandler);
            }
        });
        return bootstrap;
    }
}