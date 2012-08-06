package com.aerofs.daemon.transport.xmpp.zephyr.client.netty.handler;

import java.net.Proxy;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpClientCodec;

import com.aerofs.daemon.transport.lib.INetworkStats;
import com.aerofs.daemon.transport.xmpp.zephyr.client.netty.IZephyrIOEventSink;

public class ZephyrClientPipelineFactory implements ChannelPipelineFactory {

    private final ChannelHandler _registration;
    private final ChannelHandler _data;
    private final ChannelHandler _monitor;
    private final Proxy _proxy;
    private final IZephyrIOEventSink _sink;

    public ZephyrClientPipelineFactory(IZephyrIOEventSink sink,
            INetworkStats stats, Proxy proxy)
    {
        _registration = new ZephyrRegistrationHandler(sink);
        _data = new ZephyrClientDataHandler(sink);
        _monitor = new NetworkStatsMonitor(stats);
        _proxy = proxy;
        _sink = sink;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception
    {
        ChannelPipeline pipeline = Channels.pipeline();

        // Encoders and decoders maintain state due to buffering of incoming
        // and outgoing data, so new instances need to be created for each new
        // pipeline. The others are stateless and can be reused.

        pipeline.addLast("monitor", _monitor);

        // If a proxy is to be used, create the proxy and tunnel handler
        if (_proxy.type() == Proxy.Type.HTTP) {
            pipeline.addLast("proxy", new ConnectionProxyHandler(_proxy.address()));
            pipeline.addLast("http_codec", new HttpClientCodec());
            pipeline.addLast("tunnel", new ConnectTunnelHandler());
        } else if (_proxy.type() != Proxy.Type.DIRECT) {
            assert false : ("Proxy type unsupported: " + _proxy.type());
        }

        pipeline.addLast("encoder", new ZephyrClientFrameEncoder(_sink));
        pipeline.addLast("decoder", new ZephyrClientFrameDecoder());
        pipeline.addLast("registration", _registration);
        pipeline.addLast("data", _data);

        return pipeline;
    }

}
