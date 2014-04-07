/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.AddressResolverHandler;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.transport.lib.IIncomingChannelListener;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.handlers.CNameVerifiedHandler;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.HandlerMode;
import com.aerofs.daemon.transport.lib.handlers.IncomingChannelHandler;
import com.aerofs.daemon.transport.lib.handlers.MessageHandler;
import com.aerofs.daemon.transport.lib.handlers.ShouldKeepAcceptedChannelHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.rocklog.RockLog;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.Timer;

import static com.aerofs.base.net.NettyUtil.newCNameVerificationHandler;
import static com.aerofs.base.net.NettyUtil.newSslHandler;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newCoreProtocolVersionReader;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newCoreProtocolVersionWriter;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newFrameDecoder;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newHeartbeatHandler;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newLengthFieldPrepender;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newStatsHandler;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.setConnectTimeout;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

/**
 * Helper class to create client and server bootstraps
 */
final class TCPBootstrapFactory
{
    private final AddressResolverHandler addressResolver = new AddressResolverHandler(newSingleThreadExecutor());

    private final UserID localuser;
    private final DID localdid;
    private final long channelConnectTimeout;
    private final long heartbeatInterval;
    private final int maxFailedHeartbeats;
    private final SSLEngineFactory clientSslEngineFactory;
    private final SSLEngineFactory serverSslEngineFactory;
    private final IUnicastListener unicastListener;
    private final IncomingChannelHandler incomingChannelHandler;
    private final TransportProtocolHandler protocolHandler;
    private final TCPProtocolHandler tcpProtocolHandler;
    private final TCPChannelDiagnosticsHandler clientChannelDiagnosticsHandler;
    private final TCPChannelDiagnosticsHandler serverChannelDiagnosticsHandler;
    private final TransportStats transportStats;
    private final Timer timer;
    private final RockLog rockLog;

    TCPBootstrapFactory(
            UserID localuser,
            DID localdid,
            long channelConnectTimeout,
            long heartbeatInterval,
            int maxFailedHeartbeats,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            IUnicastListener unicastListener,
            IIncomingChannelListener serverHandlerListener,
            TransportProtocolHandler protocolHandler,
            TCPProtocolHandler tcpProtocolHandler,
            TransportStats stats,
            Timer timer,
            RockLog rockLog)
    {
        this.localuser = localuser;
        this.localdid = localdid;
        this.channelConnectTimeout = channelConnectTimeout;
        this.heartbeatInterval = heartbeatInterval;
        this.maxFailedHeartbeats = maxFailedHeartbeats;
        this.clientSslEngineFactory = clientSslEngineFactory;
        this.serverSslEngineFactory = serverSslEngineFactory;
        this.unicastListener = unicastListener;
        this.protocolHandler = protocolHandler;
        this.tcpProtocolHandler = tcpProtocolHandler;
        this.incomingChannelHandler = new IncomingChannelHandler(serverHandlerListener);
        this.clientChannelDiagnosticsHandler = new TCPChannelDiagnosticsHandler(HandlerMode.CLIENT, rockLog);
        this.serverChannelDiagnosticsHandler = new TCPChannelDiagnosticsHandler(HandlerMode.SERVER, rockLog);
        this.transportStats = stats;
        this.timer = timer;
        this.rockLog = rockLog;
    }

    ClientBootstrap newClientBootstrap(ClientSocketChannelFactory channelFactory, final ChannelTeardownHandler clientChannelTeardownHandler)
    {
        ClientBootstrap bootstrap = new ClientBootstrap(channelFactory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline()
                    throws Exception
            {
                MessageHandler messageHandler = new MessageHandler(rockLog);
                CNameVerifiedHandler verifiedHandler = new CNameVerifiedHandler(unicastListener, HandlerMode.CLIENT);

                return Channels.pipeline(
                        addressResolver,
                        newStatsHandler(transportStats),
                        newSslHandler(clientSslEngineFactory),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newCoreProtocolVersionReader(),
                        newCoreProtocolVersionWriter(),
                        newCNameVerificationHandler(verifiedHandler, localuser, localdid),
                        verifiedHandler,
                        messageHandler,
                        newHeartbeatHandler(heartbeatInterval, maxFailedHeartbeats, timer),
                        tcpProtocolHandler,
                        protocolHandler,
                        clientChannelDiagnosticsHandler,
                        clientChannelTeardownHandler);
            }
        });
        setConnectTimeout(bootstrap, channelConnectTimeout);
        return bootstrap;
    }

    ServerBootstrap newServerBootstrap(ServerSocketChannelFactory channelFactory, final ChannelTeardownHandler serverChannelTeardownHandler)
    {
        ServerBootstrap bootstrap = new ServerBootstrap(channelFactory);
        bootstrap.setParentHandler(new ShouldKeepAcceptedChannelHandler());
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline()
                    throws Exception
            {
                MessageHandler messageHandler = new MessageHandler(rockLog);
                CNameVerifiedHandler verifiedHandler = new CNameVerifiedHandler(unicastListener, HandlerMode.SERVER);

                return Channels.pipeline(
                        addressResolver,
                        newStatsHandler(transportStats),
                        newSslHandler(serverSslEngineFactory),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newCoreProtocolVersionReader(),
                        newCoreProtocolVersionWriter(),
                        newCNameVerificationHandler(verifiedHandler, localuser, localdid),
                        verifiedHandler,
                        messageHandler,
                        incomingChannelHandler,
                        newHeartbeatHandler(heartbeatInterval, maxFailedHeartbeats, timer),
                        tcpProtocolHandler,
                        protocolHandler,
                        serverChannelDiagnosticsHandler,
                        serverChannelTeardownHandler);
            }
        });
        return bootstrap;
    }
}
