/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.transport.lib.IIncomingChannelListener;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.handlers.CNameVerifiedHandler;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.HandlerMode;
import com.aerofs.daemon.transport.lib.handlers.IncomingChannelHandler;
import com.aerofs.daemon.transport.lib.handlers.MessageHandler;
import com.aerofs.daemon.transport.lib.handlers.ShouldKeepAcceptedChannelHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.util.Timer;

import static com.aerofs.base.net.NettyUtil.newCNameVerificationHandler;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newConnectTimeoutHandler;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newCoreProtocolVersionReader;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newCoreProtocolVersionWriter;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newFrameDecoder;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newHeartbeatHandler;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newLengthFieldPrepender;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newStatsHandler;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.setConnectTimeout;
import static org.jboss.netty.channel.Channels.pipeline;

/**
 * Creates {@link org.jboss.netty.bootstrap.ServerBootstrap} and
 * {@link org.jboss.netty.bootstrap.ClientBootstrap} instances to
 * allow network communication over libjingle with a remote device.
 */
final class JingleBootstrapFactory
{
    private final JingleChannelDiagnosticsHandler clientChannelDiagnosticsHandler;
    private final JingleChannelDiagnosticsHandler serverChannelDiagnosticsHandler;
    private final UserID localuser;
    private final DID localdid;
    private final long channelConnectTimeout;
    private final long heartbeatInterval;
    private final int maxFailedHeartbeats;
    private final Timer timer;
    private final SSLEngineFactory clientSslEngineFactory;
    private final SSLEngineFactory serverSslEngineFactory;
    private final IncomingChannelHandler incomingChannelHandler;
    private final TransportProtocolHandler protocolHandler;
    private final IUnicastListener unicastListener;
    private final TransportStats transportStats;
    private final SignalThread signalThread;
    private final JingleChannelWorker channelWorker;
    private final IRoundTripTimes roundTripTimes;

    JingleBootstrapFactory(
            UserID localuser,
            DID localdid,
            long channelConnectTimeout,
            long heartbeatInterval,
            int maxFailedHeartbeats,
            Timer timer,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            IUnicastListener unicastListener,
            IIncomingChannelListener serverHandlerListener,
            TransportProtocolHandler protocolHandler,
            TransportStats transportStats,
            SignalThread signalThread,
            JingleChannelWorker channelWorker,
            IRoundTripTimes roundTripTimes)
    {
        clientChannelDiagnosticsHandler = new JingleChannelDiagnosticsHandler(HandlerMode.CLIENT, roundTripTimes);
        serverChannelDiagnosticsHandler = new JingleChannelDiagnosticsHandler(HandlerMode.SERVER, roundTripTimes);
        this.localuser = localuser;
        this.localdid = localdid;
        this.channelConnectTimeout = channelConnectTimeout;
        this.heartbeatInterval = heartbeatInterval;
        this.maxFailedHeartbeats = maxFailedHeartbeats;
        this.timer = timer;
        this.clientSslEngineFactory = clientSslEngineFactory;
        this.serverSslEngineFactory = serverSslEngineFactory;
        this.incomingChannelHandler = new IncomingChannelHandler(serverHandlerListener);
        this.protocolHandler = protocolHandler;
        this.unicastListener = unicastListener;
        this.transportStats = transportStats;
        this.signalThread = signalThread;
        this.channelWorker = channelWorker;
        this.roundTripTimes = roundTripTimes;
    }

    ClientBootstrap newClientBootstrap(final ChannelTeardownHandler clientChannelTeardownHandler)
    {
        ClientBootstrap bootstrap = new ClientBootstrap(new JingleClientChannelFactory(channelConnectTimeout, timer, signalThread, channelWorker));
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline()
                    throws Exception
            {
                MessageHandler messageHandler = new MessageHandler();
                CNameVerifiedHandler verifiedHandler = new CNameVerifiedHandler(unicastListener, HandlerMode.CLIENT);

                return pipeline(
                        newStatsHandler(transportStats),
                        clientSslEngineFactory.newSslHandler(),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newCoreProtocolVersionReader(),
                        newCoreProtocolVersionWriter(),
                        newCNameVerificationHandler(verifiedHandler, localuser, localdid),
                        newConnectTimeoutHandler(channelConnectTimeout, timer),
                        verifiedHandler,
                        messageHandler,
                        newHeartbeatHandler(heartbeatInterval, maxFailedHeartbeats, timer, roundTripTimes),
                        protocolHandler,
                        clientChannelDiagnosticsHandler,
                        clientChannelTeardownHandler);
            }
        });
        setConnectTimeout(bootstrap, channelConnectTimeout);
        return bootstrap;
    }

    ServerBootstrap newServerBootstrap(final ChannelTeardownHandler serverChannelTeardownHandler)
    {
        ServerBootstrap bootstrap = new ServerBootstrap(new JingleServerChannelFactory(signalThread, channelWorker));
        bootstrap.setParentHandler(new ShouldKeepAcceptedChannelHandler());
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline()
                    throws Exception
            {
                MessageHandler messageHandler = new MessageHandler();
                CNameVerifiedHandler verifiedHandler = new CNameVerifiedHandler(unicastListener, HandlerMode.SERVER);

                return pipeline(
                        newStatsHandler(transportStats),
                        serverSslEngineFactory.newSslHandler(),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newCoreProtocolVersionReader(),
                        newCoreProtocolVersionWriter(),
                        newCNameVerificationHandler(verifiedHandler, localuser, localdid),
                        newConnectTimeoutHandler(channelConnectTimeout, timer),
                        verifiedHandler,
                        messageHandler,
                        incomingChannelHandler,
                        newHeartbeatHandler(heartbeatInterval, maxFailedHeartbeats, timer, roundTripTimes),
                        protocolHandler,
                        serverChannelDiagnosticsHandler,
                        serverChannelTeardownHandler);
            }
        });
        return bootstrap;
    }

}