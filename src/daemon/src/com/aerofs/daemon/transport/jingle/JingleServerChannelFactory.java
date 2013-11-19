/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.ServerChannel;
import org.jboss.netty.channel.ServerChannelFactory;
import org.jboss.netty.channel.group.DefaultChannelGroup;

import static com.google.common.base.Preconditions.checkState;

class JingleServerChannelFactory implements ServerChannelFactory
{
    private final DefaultChannelGroup serverChannelGroup = new DefaultChannelGroup();
    private final JingleChannelWorker channelWorker;
    private final SignalThread signalThread;
    private final ChannelSink serverChannelSink;

    private volatile boolean stopped;

    public JingleServerChannelFactory(SignalThread signalThread, JingleChannelWorker channelWorker)
    {
        this.signalThread = signalThread;
        this.channelWorker = channelWorker;
        this.serverChannelSink = new JingleServerChannelSink(channelWorker);
    }

    @Override
    public ServerChannel newChannel(ChannelPipeline pipeline)
    {
        checkState(!stopped);

        JingleServerChannel channel = new JingleServerChannel(signalThread, channelWorker, this, pipeline, serverChannelSink);
        serverChannelGroup.add(channel);

        return channel;
    }

    @Override
    public void shutdown()
    {
        stopped = true;
        serverChannelGroup.close().awaitUninterruptibly();
    }

    @Override
    public void releaseExternalResources()
    {
        shutdown();
    }
}
