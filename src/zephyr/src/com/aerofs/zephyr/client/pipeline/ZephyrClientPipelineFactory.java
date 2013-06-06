package com.aerofs.zephyr.client.pipeline;

import com.aerofs.base.net.AddressResolverHandler;
import com.aerofs.zephyr.client.IZephyrRelayedDataSink;
import com.aerofs.zephyr.client.IZephyrSignallingService;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.util.HashedWheelTimer;

import java.net.Proxy;
import java.util.concurrent.TimeUnit;

import static com.aerofs.zephyr.client.pipeline.ZephyrPipeline.RELAYED_HANDLER_NAME;
import static com.google.common.base.Preconditions.checkArgument;
import static java.net.Proxy.Type.DIRECT;
import static java.net.Proxy.Type.HTTP;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public final class ZephyrClientPipelineFactory implements ChannelPipelineFactory
{
    private final HashedWheelTimer handshakeTimeoutTimer = new HashedWheelTimer(500, MILLISECONDS);
    private final IZephyrSignallingService zephyrSignallingService;
    private final AddressResolverHandler resolver;
    private final ZephyrRelayedDataHandler relayedData;
    private final FinalUpstreamEventHandler closer = new FinalUpstreamEventHandler();
    private final Proxy proxy;
    private final long zephyrHandshakeTimeout;
    private final TimeUnit zephyrHandshakeTimeoutTimeunit;

    public ZephyrClientPipelineFactory(IZephyrSignallingService zephyrSignallingService, IZephyrRelayedDataSink zephyrRelayedDataSink, Proxy proxy, long zephyrHandshakeTimeout)
    {
        checkArgument(proxy.type() == DIRECT || proxy.type() == HTTP, "cannot support proxy type:" + proxy.type());

        this.zephyrSignallingService = zephyrSignallingService;
        this.resolver = new AddressResolverHandler(null);
        this.relayedData = new ZephyrRelayedDataHandler(zephyrRelayedDataSink);
        this.proxy = proxy;
        this.zephyrHandshakeTimeout = zephyrHandshakeTimeout;
        this.zephyrHandshakeTimeoutTimeunit = MILLISECONDS;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception
    {
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("resolver", resolver);
        pipeline.addLast("stats", new ChannelStatsHandler());

        if (proxy.type() == HTTP) {
            pipeline.addLast("proxy", new ProxiedConnectionHandler(proxy.address()));
            pipeline.addLast("http_codec", new HttpClientCodec());
            pipeline.addLast("tunnel", new ConnectTunnelHandler());
        }

        pipeline.addLast("encoder", new ZephyrFrameEncoder());
        pipeline.addLast("decoder", new ZephyrFrameDecoder());
        pipeline.addLast("protocol", new ZephyrHandshakeHandler(zephyrSignallingService, handshakeTimeoutTimer, zephyrHandshakeTimeout, zephyrHandshakeTimeoutTimeunit));

        pipeline.addLast(RELAYED_HANDLER_NAME, relayedData);

        pipeline.addLast("final", closer);
        return pipeline;
    }
}
