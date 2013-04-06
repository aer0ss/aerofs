package com.aerofs.daemon.tng.xmpp.netty;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;

/**
 * Factory that creates new {@link MockChannel}s.
 *
 */
public class MockChannelFactory implements ClientSocketChannelFactory
{
    private final MockChannelEventSink _sink;

    public MockChannelFactory(MockChannelEventSink sink)
    {
        _sink = sink;
    }

    @Override
    public SocketChannel newChannel(ChannelPipeline pipeline)
    {
        return MockChannel.createChannel(this, pipeline, _sink);
    }

    @Override
    public void shutdown()
    {}

    @Override
    public void releaseExternalResources()
    {}

}
