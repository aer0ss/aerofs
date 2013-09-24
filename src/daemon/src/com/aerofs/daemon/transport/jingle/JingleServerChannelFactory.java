/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.ServerChannel;
import org.jboss.netty.channel.ServerChannelFactory;
import org.jboss.netty.channel.group.DefaultChannelGroup;

class JingleServerChannelFactory implements ServerChannelFactory
{
    private final DefaultChannelGroup _group = new DefaultChannelGroup();
    private final ChannelSink _sink;
    private final SignalThread _signalThread;

    public JingleServerChannelFactory(SignalThread signalThread)
    {
        _signalThread = signalThread;
        _sink = new JingleServerChannelSink(signalThread);
    }

    @Override
    public ServerChannel newChannel(ChannelPipeline pipeline)
    {
        JingleServerChannel channel = new JingleServerChannel(this, pipeline, _sink, _signalThread);
        _group.add(channel);

        return channel;
    }

    @Override
    public void shutdown()
    {
        _group.close().awaitUninterruptibly();
    }

    @Override
    public void releaseExternalResources()
    {
        shutdown();
    }
}
