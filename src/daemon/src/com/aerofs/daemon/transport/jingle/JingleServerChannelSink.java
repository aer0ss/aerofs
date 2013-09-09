/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.Loggers;
import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.slf4j.Logger;

import static org.jboss.netty.channel.Channels.fireChannelBound;
import static org.jboss.netty.channel.Channels.fireExceptionCaught;


public class JingleServerChannelSink extends AbstractChannelSink
{
    private static final Logger l = Loggers.getLogger(JingleServerChannelSink.class);

    private final SignalThread _signalThread;

    public JingleServerChannelSink(SignalThread signalThread)
    {
        _signalThread = signalThread;
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

    @SuppressWarnings("fallthrough")
    private void handleServerSocket(ChannelEvent e)
    {
        if (!(e instanceof ChannelStateEvent)) return;

        ChannelStateEvent event = (ChannelStateEvent) e;
        JingleServerChannel channel = (JingleServerChannel) event.getChannel();
        ChannelFuture future = event.getFuture();
        Object value = event.getValue();

        switch (JingleUtils.parseDownstreamEvent(event.getState(), value)) {
        case BIND:
            bind(channel, future, (JingleAddress) value);
            break;
        case CLOSE:
        case UNBIND:
            unbind(channel, future);
            break;
        default:
            String msg = "j srv sink: unsupported event: " + event.getState() + "v: " + value;
            l.warn(msg);
            future.setFailure(new UnsupportedOperationException(msg));
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
            fireExceptionCaught(channel, t);
        }
    }

    private static void unbind(JingleServerChannel channel, ChannelFuture future)
    {
        // TODO (GS): Should we do something?
        future.setSuccess();
    }

    @SuppressWarnings("fallthrough")
    private void handleAcceptedSocket(ChannelEvent e)
    {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;
            JingleClientChannel channel = (JingleClientChannel) event.getChannel();
            ChannelFuture future = event.getFuture();
            Object value = event.getValue();

            switch (JingleUtils.parseDownstreamEvent(event.getState(), value)) {
            case CLOSE:
            case UNBIND:
            case DISCONNECT:
                channel.onCloseEventReceived(future, _signalThread);
                break;
            case INTEREST_OPS:
                future.setSuccess();  // Unsupported - discard silently.
                break;
            default:
                String msg = "j acc sink: unsupported event: " + event.getState() + "v: " + value;
                l.warn(msg);
                future.setFailure(new UnsupportedOperationException(msg));
                break;
            }
        } else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
            JingleClientChannel channel = (JingleClientChannel) event.getChannel();
            channel.onWriteEventReceived(event, _signalThread);
        }
    }
}
