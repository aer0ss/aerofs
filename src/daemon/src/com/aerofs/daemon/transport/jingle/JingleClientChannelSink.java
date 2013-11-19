/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.Loggers;
import com.aerofs.base.net.NettyUtil;
import com.aerofs.daemon.transport.ExTransportUnavailable;
import com.aerofs.j.StreamInterface;
import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.slf4j.Logger;

import static org.jboss.netty.channel.Channels.future;

/**
 * {@link org.jboss.netty.channel.ChannelSink} implementation
 * that handles all downstream events for a {@link JingleClientChannel}
 * created by {@link com.aerofs.daemon.transport.jingle.JingleClientChannelFactory}.
 */
class JingleClientChannelSink extends AbstractChannelSink
{
    private static final Logger l = Loggers.getLogger(JingleClientChannelSink.class);

    private final SignalThread signalThread;

    public JingleClientChannelSink(SignalThread signalThread)
    {
        this.signalThread = signalThread;
    }

    @Override
    public ChannelFuture execute(ChannelPipeline pipeline, Runnable task)
    {
        JingleClientChannel channel = (JingleClientChannel) pipeline.getChannel();
        ChannelFuture future = future(channel);
        channel.execute(future, task);
        return future;
    }

    @SuppressWarnings("fallthrough")
    @Override
    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e)
            throws Exception
    {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;
            JingleClientChannel channel = (JingleClientChannel) event.getChannel();
            ChannelFuture future = event.getFuture();
            Object value = event.getValue();

            switch (NettyUtil.parseDownstreamEvent(event.getState(), value)) {
            case BIND:
                bind(channel, future, (JingleAddress) value);
                break;
            case CONNECT:
                connect(channel, future, (JingleAddress) value);
                break;
            case CLOSE:
            case UNBIND:
            case DISCONNECT:
                channel.onCloseEventReceived(future);
                break;
            default:
                throw new UnsupportedOperationException("unsupported event:" + event.getState() + " value: " + value);
            }
        } else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
            JingleClientChannel channel = (JingleClientChannel) event.getChannel();
            channel.onWriteEventReceived(event);
        }
    }

    // as verified in https://github.com/netty/netty/blob/3.6/src/main/java/org/jboss/netty/channel/socket/nio/NioClientSocketPipelineSink.java
    // this is not triggered unless the user binds manually
    private static void bind(JingleClientChannel channel, ChannelFuture future, JingleAddress localAddress)
    {
        channel.setBound(future, localAddress);
    }

    // is connect notified on all error conditions?
    private void connect(final JingleClientChannel channel, final ChannelFuture future, final JingleAddress remoteAddress)
    {
        signalThread.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                try {
                    channel.setConnectFuture(future);

                    StreamInterface streamInterface = signalThread.createTunnel(remoteAddress.getJid(), String.format("%s->%s", channel.getLocalAddress(), channel.getRemoteAddress()));
                    channel.wrapStreamInterface(remoteAddress, false, streamInterface);

                    l.info("connecting to {}", remoteAddress.getDid());
                } catch (ExTransportUnavailable e) {
                    handleError(e);
                }
            }

            @Override
            public void error(Exception e)
            {
                handleError(e);
            }

            private void handleError(Exception e)
            {
                l.warn("failed to connect to {}", remoteAddress.getDid(), e);
                future.setFailure(e);
                channel.onClose(e);
            }
        });
    }
}
