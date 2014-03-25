/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.transport.lib.IIncomingChannelListener;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.handlers.CNameVerifiedHandler;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.HandlerMode;
import com.aerofs.daemon.transport.lib.handlers.IncomingChannelHandler;
import com.aerofs.daemon.transport.lib.handlers.MessageHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;

import static com.aerofs.base.net.NettyUtil.newCNameVerificationHandler;
import static com.aerofs.base.net.NettyUtil.newSslHandler;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newFrameDecoder;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newLengthFieldPrepender;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newCoreProtocolVersionReader;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newCoreProtocolVersionWriter;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newStatsHandler;
import static org.jboss.netty.channel.Channels.pipeline;

/**
 * Creates {@link org.jboss.netty.bootstrap.ServerBootstrap} and
 * {@link org.jboss.netty.bootstrap.ClientBootstrap} instances to
 * allow network communication over libjingle with a remote device.
 */
final class JingleBootstrapFactory
{
    private final JingleChannelDiagnosticsHandler clientChannelDiagnosticsHandler = new JingleChannelDiagnosticsHandler(HandlerMode.CLIENT);
    private final JingleChannelDiagnosticsHandler serverChannelDiagnosticsHandler = new JingleChannelDiagnosticsHandler(HandlerMode.SERVER);
    private final UserID localuser;
    private final DID localdid;
    private final SSLEngineFactory clientSslEngineFactory;
    private final SSLEngineFactory serverSslEngineFactory;
    private final IncomingChannelHandler incomingChannelHandler;
    private final TransportProtocolHandler protocolHandler;
    private final IUnicastListener unicastListener;
    private final TransportStats transportStats;
    private final SignalThread signalThread;
    private final JingleChannelWorker channelWorker;

    JingleBootstrapFactory(
            UserID localuser,
            DID localdid,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            IUnicastListener unicastListener,
            IIncomingChannelListener serverHandlerListener,
            TransportProtocolHandler protocolHandler,
            TransportStats transportStats,
            SignalThread signalThread,
            JingleChannelWorker channelWorker)
    {
        this.localuser = localuser;
        this.localdid = localdid;
        this.clientSslEngineFactory = clientSslEngineFactory;
        this.serverSslEngineFactory = serverSslEngineFactory;
        this.incomingChannelHandler = new IncomingChannelHandler(serverHandlerListener);
        this.protocolHandler = protocolHandler;
        this.unicastListener = unicastListener;
        this.transportStats = transportStats;
        this.signalThread = signalThread;
        this.channelWorker = channelWorker;
    }

    ClientBootstrap newClientBootstrap(final ChannelTeardownHandler clientChannelTeardownHandler)
    {
        ClientBootstrap bootstrap = new ClientBootstrap(new JingleClientChannelFactory(signalThread, channelWorker));
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
                        newSslHandler(clientSslEngineFactory),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newCoreProtocolVersionReader(),
                        newCoreProtocolVersionWriter(),
                        newCNameVerificationHandler(verifiedHandler, localuser, localdid),
                        verifiedHandler,
                        messageHandler,
                        protocolHandler,
                        clientChannelDiagnosticsHandler,
                        clientChannelTeardownHandler);
            }
        });
        return bootstrap;
    }

    ServerBootstrap newServerBootstrap(final ChannelTeardownHandler serverChannelTeardownHandler)
    {
        ServerBootstrap bootstrap = new ServerBootstrap(new JingleServerChannelFactory(signalThread, channelWorker));
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
                        newSslHandler(serverSslEngineFactory),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newCoreProtocolVersionReader(),
                        newCoreProtocolVersionWriter(),
                        newCNameVerificationHandler(verifiedHandler, localuser, localdid),
                        verifiedHandler,
                        messageHandler,
                        incomingChannelHandler,
                        protocolHandler,
                        serverChannelDiagnosticsHandler,
                        serverChannelTeardownHandler);
            }
        });
        return bootstrap;
    }

}