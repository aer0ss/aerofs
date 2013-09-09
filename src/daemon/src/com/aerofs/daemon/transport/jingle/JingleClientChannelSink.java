/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.j.Jid;
import com.aerofs.j.StreamInterface;
import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.slf4j.Logger;

import static org.jboss.netty.channel.Channels.fireChannelBound;
import static org.jboss.netty.channel.Channels.fireExceptionCaught;

public class JingleClientChannelSink extends AbstractChannelSink
{
    private static final Logger l = Loggers.getLogger(JingleClientChannelSink.class);
    private final SignalThread _signalThread;

    public JingleClientChannelSink(SignalThread signalThread)
    {
        _signalThread = signalThread;
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

            switch (JingleUtils.parseDownstreamEvent(event.getState(), value)) {
            case BIND:
                bind(channel, future, (JingleAddress)value);
                break;
            case CONNECT:
                connect(channel, future, (JingleAddress)value);
                break;
            case CLOSE:
            case UNBIND:
            case DISCONNECT:
                channel.onCloseEventReceived(future, _signalThread);
                break;
            case INTEREST_OPS:
                future.setSuccess(); // Unsupported - discard silently.
                break;
            }

        } else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
            JingleClientChannel channel = (JingleClientChannel) event.getChannel();
            channel.onWriteEventReceived(event, _signalThread);
        }
    }

    private static void bind(JingleClientChannel channel, ChannelFuture future, JingleAddress localAddress)
    {
        try {
            channel.setBound();
            channel.setLocalAddress(localAddress);
            future.setSuccess();
            fireChannelBound(channel, localAddress);
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private void connect(final JingleClientChannel channel, final ChannelFuture future, final JingleAddress remoteAddress)
    {
        _signalThread.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                DID did = remoteAddress.getDid();
                Jid jid = remoteAddress.getJid();

                channel.setRemoteAddress(remoteAddress);
                channel.setConnectFuture(future);

                StreamInterface s = _signalThread.createTunnel(jid, "a");
                JingleStream stream = new JingleStream(s, did, false, channel);
                channel.setJingleDataStream(stream);

                l.info("eng: connect initiated to d:" + did);
            }

            @Override
            public void error(Exception e)
            {
                future.setFailure(e);
            }
        });
    }
}
