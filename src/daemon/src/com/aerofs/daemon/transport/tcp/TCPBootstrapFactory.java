/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.AddressResolverHandler;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.transport.lib.TransportProtocolHandler;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.netty.ClientHandler;
import com.aerofs.daemon.transport.netty.ServerHandler;
import com.aerofs.daemon.transport.netty.ServerHandler.IServerHandlerListener;
import com.aerofs.rocklog.RockLog;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

import static com.aerofs.base.net.NettyUtil.newCNameVerificationHandler;
import static com.aerofs.base.net.NettyUtil.newSslHandler;
import static com.aerofs.daemon.transport.netty.BootstrapFactoryUtil.newDiagnosticsHandler;
import static com.aerofs.daemon.transport.netty.BootstrapFactoryUtil.newFrameDecoder;
import static com.aerofs.daemon.transport.netty.BootstrapFactoryUtil.newLengthFieldPrepender;
import static com.aerofs.daemon.transport.netty.BootstrapFactoryUtil.newMagicReader;
import static com.aerofs.daemon.transport.netty.BootstrapFactoryUtil.newMagicWriter;
import static com.aerofs.daemon.transport.netty.BootstrapFactoryUtil.newStatsHandler;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Helper class to create client and server bootstraps
 */
class TCPBootstrapFactory
{
    private final AddressResolverHandler _addressResolver = new AddressResolverHandler(newSingleThreadExecutor());
    private final String _transportId;
    private final UserID _localuser;
    private final DID _localdid;
    private final SSLEngineFactory _clientSslEngineFactory;
    private final SSLEngineFactory _serverSslEngineFactory;
    private final RockLog _rockLog;
    private final TransportStats _stats;
    private final Timer _timer = new HashedWheelTimer(500, MILLISECONDS);

    TCPBootstrapFactory(String transportId, UserID localUser, DID localDID, SSLEngineFactory clientSslEngineFactory, SSLEngineFactory serverSslEngineFactory, RockLog rockLog, TransportStats stats)
    {
        _transportId = transportId;
        _localuser = localUser;
        _localdid = localDID;
        _clientSslEngineFactory = clientSslEngineFactory;
        _serverSslEngineFactory = serverSslEngineFactory;
        _rockLog = rockLog;
        _stats = stats;
    }

    ClientBootstrap newClientBootstrap(ClientSocketChannelFactory channelFactory)
    {
        ClientBootstrap bootstrap = new ClientBootstrap(channelFactory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                ClientHandler clientHandler = new ClientHandler();

                return Channels.pipeline(
                        _addressResolver,
                        newStatsHandler(_stats),
                        newSslHandler(_clientSslEngineFactory),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newMagicReader(),
                        newMagicWriter(),
                        newCNameVerificationHandler(clientHandler, _localuser, _localdid),
                        newDiagnosticsHandler(_transportId, _rockLog, _timer),
                        clientHandler);
            }
        });
        return bootstrap;
    }

    ServerBootstrap newServerBootstrap(ServerSocketChannelFactory channelFactory,
            final IServerHandlerListener listener, final TCPProtocolHandler tcpProtocolHandler, final TransportProtocolHandler protocolHandler)
    {
        ServerBootstrap bootstrap = new ServerBootstrap(channelFactory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                ServerHandler serverHandler = new ServerHandler(listener);

                return Channels.pipeline(
                        _addressResolver,
                        newStatsHandler(_stats),
                        newSslHandler(_serverSslEngineFactory),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newMagicReader(),
                        newMagicWriter(),
                        newCNameVerificationHandler(serverHandler, _localuser, _localdid),
                        newDiagnosticsHandler(_transportId, _rockLog, _timer),
                        serverHandler,
                        tcpProtocolHandler,
                        protocolHandler);
            }
        });
        return bootstrap;
    }
}
