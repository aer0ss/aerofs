/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.ClientHandler;
import com.aerofs.daemon.transport.lib.handlers.ServerHandler;
import com.aerofs.daemon.transport.lib.handlers.ServerHandler.IServerHandlerListener;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;

import static com.aerofs.base.net.NettyUtil.newCNameVerificationHandler;
import static com.aerofs.base.net.NettyUtil.newSslHandler;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newFrameDecoder;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newLengthFieldPrepender;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newMagicReader;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newMagicWriter;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newStatsHandler;

/**
 * Creates {@link org.jboss.netty.bootstrap.ServerBootstrap} and
 * {@link org.jboss.netty.bootstrap.ClientBootstrap} instances to
 * allow network communication over libjingle with a remote device.
 */
final class JingleBootstrapFactory
{
    private final UserID localuser;
    private final DID localdid;
    private final SSLEngineFactory clientSslEngineFactory;
    private final SSLEngineFactory serverSslEngineFactory;
    private final IUnicastListener unicastListener;
    private final TransportStats transportStats;
    private final JingleChannelWorker channelWorker;

    JingleBootstrapFactory(
            UserID localuser,
            DID localdid,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            IUnicastListener unicastListener,
            TransportStats transportStats,
            JingleChannelWorker channelWorker)
    {
        this.localuser = localuser;
        this.localdid = localdid;
        this.clientSslEngineFactory = clientSslEngineFactory;
        this.serverSslEngineFactory = serverSslEngineFactory;
        this.unicastListener = unicastListener;
        this.transportStats = transportStats;
        this.channelWorker = channelWorker;
    }

    ClientBootstrap newClientBootstrap(SignalThread signalThread, final ChannelTeardownHandler clientChannelTeardownHandler)
    {
        ClientBootstrap bootstrap = new ClientBootstrap(new JingleClientChannelFactory(signalThread, channelWorker));
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                ClientHandler clientHandler = new ClientHandler(unicastListener);

                return Channels.pipeline(
                        newStatsHandler(transportStats),
                        newSslHandler(clientSslEngineFactory),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newMagicReader(),
                        newMagicWriter(),
                        newCNameVerificationHandler(clientHandler, localuser, localdid),
                        clientHandler,
                        clientChannelTeardownHandler);
            }
        });
        return bootstrap;
    }

    ServerBootstrap newServerBootstrap(
            SignalThread signalThread,
            final IServerHandlerListener serverHandlerListener,
            final TransportProtocolHandler protocolHandler,
            final ChannelTeardownHandler serverChannelTeardownHandler)
    {
        ServerBootstrap bootstrap = new ServerBootstrap(new JingleServerChannelFactory(signalThread, channelWorker));
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                ServerHandler serverHandler = new ServerHandler(unicastListener, serverHandlerListener);

                return Channels.pipeline(
                        newStatsHandler(transportStats),
                        newSslHandler(serverSslEngineFactory),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newMagicReader(),
                        newMagicWriter(),
                        newCNameVerificationHandler(serverHandler, localuser, localdid),
                        serverHandler,
                        protocolHandler,
                        serverChannelTeardownHandler);
            }
        });
        return bootstrap;
    }
}