/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.AddressResolverHandler;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.ClientHandler;
import com.aerofs.daemon.transport.lib.handlers.ServerHandler;
import com.aerofs.daemon.transport.lib.handlers.ServerHandler.IServerHandlerListener;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
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
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newDiagnosticsHandler;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newFrameDecoder;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newLengthFieldPrepender;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newMagicReader;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newMagicWriter;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newStatsHandler;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Helper class to create client and server bootstraps
 */
final class TCPBootstrapFactory
{
    private final AddressResolverHandler _addressResolver = new AddressResolverHandler(newSingleThreadExecutor());
    private final String _transportId;
    private final UserID _localuser;
    private final DID _localdid;
    private final SSLEngineFactory _clientSslEngineFactory;
    private final SSLEngineFactory _serverSslEngineFactory;
    private final IUnicastListener _unicastListener;
    private final RockLog _rockLog;
    private final TransportStats _stats;
    private final Timer _timer = new HashedWheelTimer(500, MILLISECONDS);

    TCPBootstrapFactory(String transportId, UserID localUser, DID localDID, SSLEngineFactory clientSslEngineFactory, SSLEngineFactory serverSslEngineFactory, IUnicastListener unicastListener, RockLog rockLog, TransportStats stats)
    {
        _transportId = transportId;
        _localuser = localUser;
        _localdid = localDID;
        _clientSslEngineFactory = clientSslEngineFactory;
        _serverSslEngineFactory = serverSslEngineFactory;
        _unicastListener = unicastListener;
        _rockLog = rockLog;
        _stats = stats;
    }

    ClientBootstrap newClientBootstrap(ClientSocketChannelFactory channelFactory, final ChannelTeardownHandler clientChannelTeardownHandler)
    {
        ClientBootstrap bootstrap = new ClientBootstrap(channelFactory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                ClientHandler clientHandler = new ClientHandler(_unicastListener);

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
                        clientHandler,
                        clientChannelTeardownHandler);
            }
        });
        return bootstrap;
    }

    ServerBootstrap newServerBootstrap(
            ServerSocketChannelFactory channelFactory,
            final IServerHandlerListener serverHandlerListener,
            final TCPProtocolHandler tcpProtocolHandler,
            final TransportProtocolHandler protocolHandler,
            final ChannelTeardownHandler serverChannelTeardownHandler)
    {
        ServerBootstrap bootstrap = new ServerBootstrap(channelFactory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                ServerHandler serverHandler = new ServerHandler(_unicastListener, serverHandlerListener);

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
                        protocolHandler,
                        serverChannelTeardownHandler);
            }
        });
        return bootstrap;
    }
}
