package com.aerofs.daemon.transport.jingle;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;

class JingleClientChannelFactory implements ChannelFactory
{
    private final ChannelSink _sink;

    public JingleClientChannelFactory(SignalThread signalThread)
    {
        _sink = new JingleClientChannelSink(signalThread);
    }

    @Override
    public Channel newChannel(ChannelPipeline pipeline)
    {
        return new JingleClientChannel(null, this, pipeline, _sink);
    }

    @Override
    public void shutdown()
    {
    }

    @Override
    public void releaseExternalResources()
    {
    }
}
