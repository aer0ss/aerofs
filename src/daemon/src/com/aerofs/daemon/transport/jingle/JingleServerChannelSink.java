/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.net.NettyUtil;
import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;

import static com.google.common.base.Preconditions.checkArgument;
import static org.jboss.netty.channel.Channels.fireChannelBound;
import static org.jboss.netty.channel.Channels.fireChannelClosedLater;
import static org.jboss.netty.channel.Channels.fireChannelUnboundLater;
import static org.jboss.netty.channel.Channels.fireExceptionCaughtLater;
import static org.jboss.netty.channel.Channels.future;

/**
 * {@link org.jboss.netty.channel.ChannelSink} implementation
 * that handles downstream events for <strong>both</strong>
 * {@link JingleServerChannel} and accepted {@link JingleClientChannel}
 * instances.
 */
class JingleServerChannelSink extends AbstractChannelSink
{
    private final JingleChannelWorker channelWorker;

    JingleServerChannelSink(JingleChannelWorker channelWorker)
    {
        this.channelWorker = channelWorker;
    }

    @Override
    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e)
            throws Exception
    {
        Channel channel = e.getChannel();
        if (channel instanceof JingleServerChannel) {
            handleServerSocket(e);
        } else if (channel instanceof JingleClientChannel) {
            handleAcceptedSocket(e);
        }
    }

    @Override
    public ChannelFuture execute(ChannelPipeline pipeline, final Runnable task)
    {
        Channel channel = pipeline.getChannel();
        checkArgument(channel instanceof JingleServerChannel, "channel:%s", channel.getClass().getSimpleName());

        final ChannelFuture executeFuture = future(channel);
        channelWorker.submitChannelTask(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    task.run();
                    executeFuture.setSuccess();
                } catch (Exception e) {
                    executeFuture.setFailure(e);
                }
            }
        });

        return executeFuture;
    }

    @SuppressWarnings("fallthrough")
    private void handleServerSocket(ChannelEvent e)
    {
        if (!(e instanceof ChannelStateEvent)) return;

        ChannelStateEvent event = (ChannelStateEvent) e;
        JingleServerChannel channel = (JingleServerChannel) event.getChannel();
        ChannelFuture future = event.getFuture();
        Object value = event.getValue();

        switch (NettyUtil.parseDownstreamEvent(event.getState(), value)) {
        case BIND:
            bind(channel, future, (JingleAddress) value);
            break;
        case CLOSE:
        case UNBIND:
            unbind(channel, future);
            break;
        default:
            future.setFailure(new UnsupportedOperationException("unsupported event:" + event.getState() + " value: " + value));
            break;
        }
    }

    private static void bind(JingleServerChannel channel, ChannelFuture future, JingleAddress localAddress)
    {
        try {
            channel.setLocalAddress(localAddress);
            future.setSuccess();
            fireChannelBound(channel, localAddress);
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaughtLater(channel, t);
        }
    }

    // server channel closing loginc copied from:
    // https://github.com/netty/netty/blob/3.6/src/main/java/org/jboss/netty/channel/socket/nio/NioServerBoss.java
    private static void unbind(JingleServerChannel channel, ChannelFuture future)
    {
        boolean bound = channel.isBound();

        try {
            if (channel.setClosed()) {
                future.setSuccess();

                if (bound) {
                    fireChannelUnboundLater(channel);
                }

                fireChannelClosedLater(channel);
            } else {
                future.setSuccess();
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaughtLater(channel, t);
        }
    }

    @SuppressWarnings("fallthrough")
    private void handleAcceptedSocket(ChannelEvent e)
    {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;
            JingleClientChannel channel = (JingleClientChannel) event.getChannel();
            ChannelFuture future = event.getFuture();
            Object value = event.getValue();

            switch (NettyUtil.parseDownstreamEvent(event.getState(), value)) {
            case CLOSE:
            case UNBIND:
            case DISCONNECT:
                channel.onCloseEventReceived(future);
                break;
            default:
                future.setFailure(new UnsupportedOperationException("unsupported event:" + event.getState() + " value: " + value));
                break;
            }
        } else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
            JingleClientChannel channel = (JingleClientChannel) event.getChannel();
            channel.onWriteEventReceived(event);
        }
    }
}
