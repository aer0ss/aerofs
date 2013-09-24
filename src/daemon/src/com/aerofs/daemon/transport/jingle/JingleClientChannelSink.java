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

class JingleClientChannelSink extends AbstractChannelSink
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

    // FIXME (AG): it appears that this is never triggered, and the local address is never set
    private static void bind(JingleClientChannel channel, ChannelFuture future, JingleAddress localAddress)
    {
        try {
            channel.setLocalAddress(localAddress);
            channel.setBound();
            future.setSuccess();
            fireChannelBound(channel, localAddress);
        } catch (Throwable t) {
            l.warn("fail bind d:{} c:{}", localAddress.getDid(), channel, t);
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    // is connect notified on all error conditions?
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

                StreamInterface s = _signalThread.createTunnel(jid, String.format("%s->%s", channel.getLocalAddress(), channel.getRemoteAddress()));
                JingleStream stream = new JingleStream(did, s, false, channel);
                channel.setJingleStream(stream);

                l.info("connect initiated to {}", did);
            }

            @Override
            public void error(Exception e)
            {
                l.warn("fail connect d:{} c:{}", remoteAddress.getDid(), channel, e);
                future.setFailure(e);
            }
        });
    }
}
