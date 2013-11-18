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
import com.aerofs.rocklog.RockLog;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.util.HashedWheelTimer;

import static com.aerofs.base.net.NettyUtil.newCNameVerificationHandler;
import static com.aerofs.base.net.NettyUtil.newSslHandler;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newDiagnosticsHandler;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newFrameDecoder;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newLengthFieldPrepender;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newMagicReader;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newMagicWriter;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newStatsHandler;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Helper class to create client and server bootstraps
 */
class JingleBootstrapFactory
{
    private final String _transportId;
    private final UserID _localuser;
    private final DID _localdid;
    private final SSLEngineFactory _clientSslEngineFactory;
    private final SSLEngineFactory _serverSslEngineFactory;
    private final IUnicastListener _unicastListener;
    private final RockLog _rockLog;
    private final TransportStats _stats;
    private final HashedWheelTimer _timer = new HashedWheelTimer(500, MILLISECONDS);

    JingleBootstrapFactory(String transportId, UserID localuser, DID localdid, SSLEngineFactory clientSslEngineFactory, SSLEngineFactory serverSslEngineFactory, IUnicastListener unicastListener, RockLog rockLog, TransportStats stats)
    {
        _transportId = transportId;
        _localuser = localuser;
        _localdid = localdid;
        _clientSslEngineFactory = clientSslEngineFactory;
        _serverSslEngineFactory = serverSslEngineFactory;
        _unicastListener = unicastListener;
        _rockLog = rockLog;
        _stats = stats;
    }

    ClientBootstrap newClientBootstrap(SignalThread signalThread, final ChannelTeardownHandler clientChannelTeardownHandler)
    {
        ClientBootstrap bootstrap = new ClientBootstrap(new JingleClientChannelFactory(signalThread));
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                ClientHandler clientHandler = new ClientHandler(_unicastListener);

                return Channels.pipeline(
                        newStatsHandler(_stats),
                        newSslHandler(_clientSslEngineFactory),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newMagicReader(),
                        newMagicWriter(),
                        newCNameVerificationHandler(clientHandler, _localuser, _localdid),
                        newDiagnosticsHandler(_transportId, _rockLog, _timer),
                        clientHandler, clientChannelTeardownHandler);
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
        ServerBootstrap bootstrap = new ServerBootstrap(new JingleServerChannelFactory(signalThread));
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                ServerHandler serverHandler = new ServerHandler(_unicastListener, serverHandlerListener);

                return Channels.pipeline(
                        newStatsHandler(_stats),
                        newSslHandler(_serverSslEngineFactory),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newMagicReader(),
                        newMagicWriter(),
                        newCNameVerificationHandler(serverHandler, _localuser, _localdid),
                        newDiagnosticsHandler(_transportId, _rockLog, _timer),
                        serverHandler,
                        protocolHandler, serverChannelTeardownHandler);
            }
        });
        return bootstrap;
    }
}