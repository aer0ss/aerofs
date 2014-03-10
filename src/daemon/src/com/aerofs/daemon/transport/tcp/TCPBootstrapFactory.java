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
import com.aerofs.daemon.transport.lib.handlers.CNameVerifiedHandler.HandlerMode;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.IncomingChannelHandler;
import com.aerofs.daemon.transport.lib.handlers.MessageHandler;
import com.aerofs.daemon.transport.lib.handlers.ShouldKeepAcceptedChannelHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;

import static com.aerofs.base.net.NettyUtil.newCNameVerificationHandler;
import static com.aerofs.base.net.NettyUtil.newSslHandler;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newFrameDecoder;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newLengthFieldPrepender;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newMagicReader;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newMagicWriter;
import static com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.newStatsHandler;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

/**
 * Helper class to create client and server bootstraps
 */
final class TCPBootstrapFactory
{
    private final AddressResolverHandler addressResolver = new AddressResolverHandler(newSingleThreadExecutor());
    private final UserID localuser;
    private final DID localdid;
    private final SSLEngineFactory clientSslEngineFactory;
    private final SSLEngineFactory serverSslEngineFactory;
    private final IUnicastListener unicastListener;
    private final TransportProtocolHandler protocolHandler;
    private final TCPProtocolHandler tcpProtocolHandler;
    private final IncomingChannelHandler incomingChannelHandler;
    private final TransportStats transportStats;

    TCPBootstrapFactory(
            UserID localuser,
            DID localdid,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            IUnicastListener unicastListener,
            IIncomingChannelListener serverHandlerListener,
            TransportProtocolHandler protocolHandler,
            TCPProtocolHandler tcpProtocolHandler,
            TransportStats stats)
    {
        this.localuser = localuser;
        this.localdid = localdid;
        this.clientSslEngineFactory = clientSslEngineFactory;
        this.serverSslEngineFactory = serverSslEngineFactory;
        this.unicastListener = unicastListener;
        this.protocolHandler = protocolHandler;
        this.tcpProtocolHandler = tcpProtocolHandler;
        this.incomingChannelHandler = new IncomingChannelHandler(serverHandlerListener);
        this.transportStats = stats;
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
                MessageHandler messageHandler = new MessageHandler();
                CNameVerifiedHandler verifiedHandler = new CNameVerifiedHandler(unicastListener, HandlerMode.CLIENT);

                return Channels.pipeline(
                        addressResolver,
                        newStatsHandler(transportStats),
                        newSslHandler(clientSslEngineFactory),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newMagicReader(),
                        newMagicWriter(),
                        newCNameVerificationHandler(verifiedHandler, localuser, localdid),
                        verifiedHandler,
                        messageHandler,
                        tcpProtocolHandler,
                        protocolHandler,
                        clientChannelTeardownHandler);
            }
        });
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
                MessageHandler messageHandler = new MessageHandler();
                CNameVerifiedHandler verifiedHandler = new CNameVerifiedHandler(unicastListener, HandlerMode.SERVER);

                return Channels.pipeline(
                        addressResolver,
                        newStatsHandler(transportStats),
                        newSslHandler(serverSslEngineFactory),
                        newFrameDecoder(),
                        newLengthFieldPrepender(),
                        newMagicReader(),
                        newMagicWriter(),
                        newCNameVerificationHandler(verifiedHandler, localuser, localdid),
                        verifiedHandler,
                        messageHandler,
                        incomingChannelHandler,
                        tcpProtocolHandler,
                        protocolHandler,
                        serverChannelTeardownHandler);
            }
        });
        return bootstrap;
    }
}
