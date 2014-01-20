package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.AddressResolverHandler;
import com.aerofs.base.ssl.CNameVerificationHandler;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.ConnectTunnelHandler;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.daemon.transport.lib.handlers.ProxiedConnectionHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
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
    private static final String ZEPHYR_CLIENT_HANDLER_NAME = "client";

    private final UserID localid;
    private final DID localdid;
    private final SSLEngineFactory clientSslEngineFactory;
    private final SSLEngineFactory serverSslEngineFactory;
    private final TransportProtocolHandler transportProtocolHandler;
    private final ChannelTeardownHandler channelTeardownHandler;
    private final TransportStats transportStats;
    private final IZephyrSignallingService zephyrSignallingService;
    private final IUnicastListener unicastListener;
    private final AddressResolverHandler resolver;
    private final CoreFrameEncoder coreFrameEncoder;
    private final Proxy proxy;
    private final long zephyrHandshakeTimeout;
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
            Proxy proxy,
            long zephyrHandshakeTimeout)
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
        this.resolver = new AddressResolverHandler(null);
        this.coreFrameEncoder = new CoreFrameEncoder();
        this.proxy = proxy;
        this.zephyrHandshakeTimeout = zephyrHandshakeTimeout;
        this.zephyrHandshakeTimeoutTimeunit = MILLISECONDS;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception
    {
        ChannelPipeline pipeline = Channels.pipeline();

        // non-protocol-specific
        pipeline.addLast("resolver", getResolverHandler());
        pipeline.addLast("stats", newStatsHandler());

        // proxy
        if (proxy.type() == HTTP) addProxyHandlers(pipeline);

        // zephyr protocol
        // since this is removed from the pipeline, I need to keep
        // this instance around and pass it to any handler that needs it
        ZephyrProtocolHandler zephyrProtocolHandler = newZephyrProtocolHandler();
        pipeline.addLast("protocol", zephyrProtocolHandler);

        // ssl
        CNameVerificationHandler cNameVerificationHandler = newCNameVerificationHandler();
        pipeline.addLast("standinssl", newStandInSslHandler(zephyrProtocolHandler));
        pipeline.addLast("cname", cNameVerificationHandler);
        pipeline.addLast("coredecoder", new CoreFrameDecoder());
        pipeline.addLast("coreencoder", coreFrameEncoder);

        // zephyr client
        ZephyrClientHandler zephyrClientHandler = newZephyrClientHandler(zephyrProtocolHandler);
        pipeline.addLast(ZEPHYR_CLIENT_HANDLER_NAME, zephyrClientHandler);

        // set up the cname listener
        cNameVerificationHandler.setListener(zephyrClientHandler);

        // setup the actual transport protocol handler
        pipeline.addLast("tpprotocol", transportProtocolHandler);

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

    private ZephyrClientHandler newZephyrClientHandler(ZephyrProtocolHandler zephyrProtocolHandler)
    {
        return new ZephyrClientHandler(localdid, unicastListener, zephyrProtocolHandler);
    }

    /**
     * Convenience method to return the {@code ZephyrClientHandler} instance from a channel
     * @param channel Channel from which to get the handler instance
     */
    static ZephyrClientHandler getZephyrClientHandler(Channel channel)
    {
        return (ZephyrClientHandler) channel.getPipeline().get(ZEPHYR_CLIENT_HANDLER_NAME);
    }
}
