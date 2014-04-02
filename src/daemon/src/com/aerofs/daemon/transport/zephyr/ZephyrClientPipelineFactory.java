package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.AddressResolverHandler;
import com.aerofs.base.ssl.CNameVerificationHandler;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.transport.lib.BootstrapFactoryUtil;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.handlers.CNameVerifiedHandler;
import com.aerofs.daemon.transport.lib.handlers.HandlerMode;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.ConnectTunnelHandler;
import com.aerofs.daemon.transport.lib.handlers.HeartbeatHandler;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.daemon.transport.lib.handlers.MessageHandler;
import com.aerofs.daemon.transport.lib.handlers.ProxiedConnectionHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.rocklog.RockLog;
import com.aerofs.zephyr.client.IZephyrSignallingService;
import com.aerofs.zephyr.client.handlers.ZephyrProtocolHandler;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.util.HashedWheelTimer;

import java.net.Proxy;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static java.net.Proxy.Type.DIRECT;
import static java.net.Proxy.Type.HTTP;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

final class ZephyrClientPipelineFactory implements ChannelPipelineFactory
{
    private static final String CNAME_VERIFIED_HANDLER_NAME = "cname-verified";
    private static final String MESSAGE_HANDLER_NAME = "message";
    private static final String ZEPHYR_CLIENT_HANDLER_NAME = "zephyr-client";

    private final UserID localid;
    private final DID localdid;
    private final SSLEngineFactory clientSslEngineFactory;
    private final SSLEngineFactory serverSslEngineFactory;
    private final TransportProtocolHandler transportProtocolHandler;
    private final ChannelTeardownHandler channelTeardownHandler;
    private final TransportStats transportStats;
    private final IZephyrSignallingService zephyrSignallingService;
    private final IUnicastListener unicastListener;
    private final RockLog rockLog;
    private final AddressResolverHandler resolver;
    private final Proxy proxy;
    private final long zephyrHandshakeTimeout;
    private final long heartbeatInterval;
    private final int maxFailedHeartbeats;
    private final TimeUnit zephyrHandshakeTimeoutTimeunit;
    private final HashedWheelTimer timer = new HashedWheelTimer(500, MILLISECONDS);

    public ZephyrClientPipelineFactory(
            UserID localid,
            DID localdid,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            TransportProtocolHandler transportProtocolHandler,
            ChannelTeardownHandler channelTeardownHandler,
            TransportStats transportStats,
            IZephyrSignallingService zephyrSignallingService,
            IUnicastListener unicastListener,
            RockLog rockLog,
            Proxy proxy,
            long zephyrHandshakeTimeout,
            long heartbeatInterval,
            int maxFailedHeartbeats)
    {
        checkArgument(proxy.type() == DIRECT || proxy.type() == HTTP, "cannot support proxy type:" + proxy.type());

        this.localid = localid;
        this.localdid = localdid;
        this.clientSslEngineFactory = clientSslEngineFactory;
        this.serverSslEngineFactory = serverSslEngineFactory;
        this.transportProtocolHandler = transportProtocolHandler;
        this.channelTeardownHandler = channelTeardownHandler;
        this.transportStats = transportStats;
        this.zephyrSignallingService = zephyrSignallingService;
        this.unicastListener = unicastListener;
        this.rockLog = rockLog;
        this.resolver = new AddressResolverHandler(null);
        this.proxy = proxy;
        this.zephyrHandshakeTimeout = zephyrHandshakeTimeout;
        this.heartbeatInterval = heartbeatInterval;
        this.maxFailedHeartbeats = maxFailedHeartbeats;
        this.zephyrHandshakeTimeoutTimeunit = MILLISECONDS;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception
    {
        ChannelPipeline pipeline = Channels.pipeline();

        // address resolution
        pipeline.addLast("address-resolver", getResolverHandler());

        // statistics
        IOStatsHandler ioStatsHandler = newStatsHandler();
        pipeline.addLast("stats", ioStatsHandler);

        // proxy
        if (proxy.type() == HTTP) addProxyHandlers(pipeline);

        // zephyr protocol
        // since this is removed from the pipeline, I need to keep
        // this instance around and pass it to any handler that needs it
        ZephyrProtocolHandler zephyrProtocolHandler = newZephyrProtocolHandler();
        pipeline.addLast("zephyr-protocol", zephyrProtocolHandler);

        // ssl
        CNameVerificationHandler verificationHandler = newCNameVerificationHandler();
        pipeline.addLast("standinssl", newStandInSslHandler(zephyrProtocolHandler));
        pipeline.addLast("cname", verificationHandler);

        // set up the cname listener
        CNameVerifiedHandler verifiedHandler = newCNameVerifiedHandler();
        pipeline.addLast(CNAME_VERIFIED_HANDLER_NAME, verifiedHandler);
        verificationHandler.setListener(verifiedHandler);

        // framing
        pipeline.addLast("length-decoder", BootstrapFactoryUtil.newFrameDecoder());
        pipeline.addLast("length-encoder", BootstrapFactoryUtil.newLengthFieldPrepender());

        // core-protocol-version
        pipeline.addLast("version-reader", BootstrapFactoryUtil.newCoreProtocolVersionReader());
        pipeline.addLast("version-writer", BootstrapFactoryUtil.newCoreProtocolVersionWriter());

        // set up the main send/recv message handler
        pipeline.addLast(MESSAGE_HANDLER_NAME, newMessageHandler());

        // zephyr client
        ZephyrClientHandler zephyrClientHandler = newZephyrClientHandler(ioStatsHandler, zephyrProtocolHandler);
        pipeline.addLast(ZEPHYR_CLIENT_HANDLER_NAME, zephyrClientHandler);

        // setup the heartbeat handler
        pipeline.addLast("heartbeat", new HeartbeatHandler(heartbeatInterval, maxFailedHeartbeats, timer));

        // setup the actual transport protocol handler
        pipeline.addLast("transport-protocol", transportProtocolHandler);

        // set up the handler to teardown sessions on disconnect
        pipeline.addLast("teardown", channelTeardownHandler);

        return pipeline;
    }

    private AddressResolverHandler getResolverHandler()
    {
        return resolver;
    }

    private IOStatsHandler newStatsHandler()
    {
        return new IOStatsHandler(transportStats);
    }

    private void addProxyHandlers(ChannelPipeline pipeline)
    {
        pipeline.addLast("proxy", new ProxiedConnectionHandler(proxy.address()));
        pipeline.addLast("http_codec", new HttpClientCodec());
        pipeline.addLast("tunnel", new ConnectTunnelHandler());
    }

    private ZephyrProtocolHandler newZephyrProtocolHandler()
    {
        return new ZephyrProtocolHandler(zephyrSignallingService, timer, zephyrHandshakeTimeout, zephyrHandshakeTimeoutTimeunit);
    }

    private CNameVerificationHandler newCNameVerificationHandler()
    {
        return new CNameVerificationHandler(localid, localdid);
    }

    private StandInSslHandler newStandInSslHandler(ZephyrProtocolHandler zephyrProtocolHandler)
    {
        return new StandInSslHandler(clientSslEngineFactory, serverSslEngineFactory, zephyrProtocolHandler);
    }

    private CNameVerifiedHandler newCNameVerifiedHandler()
    {
        return new CNameVerifiedHandler(unicastListener, HandlerMode.CLIENT);
    }

    private MessageHandler newMessageHandler()
    {
        return new MessageHandler(rockLog);
    }

    private ZephyrClientHandler newZephyrClientHandler(IOStatsHandler ioStatsHandler, ZephyrProtocolHandler zephyrProtocolHandler)
    {
        return new ZephyrClientHandler(unicastListener, ioStatsHandler, zephyrProtocolHandler);
    }

    /**
     * Convenience method to return the {@code CNameVerifiedHandler} instance from a channel
     * @param channel Channel from which to get the handler instance
     */
    static CNameVerifiedHandler getCNameVerifiedHandler(Channel channel)
    {
        return (CNameVerifiedHandler) channel.getPipeline().get(CNAME_VERIFIED_HANDLER_NAME);
    }

    /**
     * Convenience method to return the {@code MessageHandler} instance from a channel
     * @param channel Channel from which to get the handler instance
     */
    static MessageHandler getMessageHandler(Channel channel)
    {
        return (MessageHandler) channel.getPipeline().get(MESSAGE_HANDLER_NAME);
    }

    /**
     * Convenience method to return the {@code ZephyrClientHandler} instance from a channel
     * @param channel Channel from which to get the handler instance
     */
    static ZephyrClientHandler getZephyrClient(Channel channel)
    {
        return (ZephyrClientHandler) channel.getPipeline().get(ZEPHYR_CLIENT_HANDLER_NAME);
    }
}
