package com.aerofs.daemon.transport.jingle;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;

import static com.google.common.base.Preconditions.checkState;

/**
 * {@code ChannelFactory} implementation that creates {@link JingleClientChannel} instances.
 */
final class JingleClientChannelFactory implements ChannelFactory
{
    private final ChannelGroup clientChannelGroup = new DefaultChannelGroup();
    private final ChannelSink clientChannelSink;
    private final SignalThread signalThread;
    private final JingleChannelWorker channelWorker;

    private volatile boolean stopped;

    public JingleClientChannelFactory(SignalThread signalThread, JingleChannelWorker channelWorker)
    {
        this.signalThread = signalThread;
        this.channelWorker = channelWorker;
        this.clientChannelSink = new JingleClientChannelSink(signalThread);
    }

    @Override
    public Channel newChannel(ChannelPipeline pipeline)
    {
        checkState(!stopped);

        JingleClientChannel channel = new JingleClientChannel(signalThread, channelWorker, null, this, pipeline, clientChannelSink);
        clientChannelGroup.add(channel);

        return channel;
    }

    @Override
    public void shutdown()
    {
        stopped = true;
        clientChannelGroup.close().awaitUninterruptibly();
    }

    @Override
    public void releaseExternalResources()
    {
        shutdown();
    }
}
