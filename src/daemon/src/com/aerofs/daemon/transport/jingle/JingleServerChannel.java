/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.jingle.SignalThread.ISignalThreadListener;
import com.aerofs.j.Jid;
import com.aerofs.j.SWIGTYPE_p_cricket__Session;
import com.aerofs.j.TunnelSessionClient;
import com.google.common.collect.Maps;
import org.jboss.netty.channel.AbstractServerChannel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DefaultServerChannelConfig;
import org.slf4j.Logger;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.jboss.netty.channel.Channels.fireChannelBound;

class JingleServerChannel extends AbstractServerChannel implements ISignalThreadListener
{
    private static final Logger l = Loggers.getLogger(AbstractServerChannel.class);
    private volatile JingleAddress _localAddress;
    private final ChannelConfig _config;

    private final ConcurrentMap<DID, JingleClientChannel> _acceptedChannels = Maps.newConcurrentMap();

    /**
     * Creates a new instance.
     *
     * @param factory the factory which created this channel
     * @param pipeline the pipeline which is going to be attached to this channel
     * @param sink the sink which will receive downstream events from the pipeline and send upstream
     * events to the pipeline
     */
    protected JingleServerChannel(ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink, SignalThread signalThread)
    {
        super(factory, pipeline, sink);
        _config = new DefaultServerChannelConfig();
        signalThread.setListener(this);
        Channels.fireChannelOpen(this);
    }

    @Override
    public ChannelConfig getConfig()
    {
        return _config;
    }

    @Override
    public boolean isBound()
    {
        return _localAddress != null;
    }

    void setLocalAddress(JingleAddress address)
    {
        _localAddress = address;
    }

    @Override
    public SocketAddress getLocalAddress()
    {
        return _localAddress;
    }

    @Override
    public SocketAddress getRemoteAddress()
    {
        return null;
    }

    /**
     * Called by libjingle when there's an incoming tunnel
     * Runs in the signal thread
     */
    @Override
    public void onIncomingTunnel(TunnelSessionClient client, Jid jid, SWIGTYPE_p_cricket__Session session)
    {
        if (_localAddress == null) {
            declineTunnel(client, jid, session, "server channel not setup");
            return;
        }

        JingleClientChannel channel = null;
        try {
            final DID did = JingleUtils.jid2did(jid);
            final ChannelPipeline pipeline = getConfig().getPipelineFactory().getPipeline();

            channel = new JingleClientChannel(this, getFactory(), pipeline, getPipeline().getSink());
            channel.getCloseFuture().addListener(new ChannelFutureListener()
            {
                @Override
                public void operationComplete(ChannelFuture future)
                        throws Exception
                {
                    _acceptedChannels.remove(did, future.getChannel());
                }
            });

            JingleClientChannel previous = _acceptedChannels.put(did, channel);
            if (previous != null) {
                l.warn("replace channel to d:{} c:{}", did, previous);
                previous.close();
            }

            l.debug("new incoming channel j:{} d:{} c:{}", jid.Str(), did, channel);

            JingleStream stream = null;
            try {
                stream = new JingleStream(did, client.AcceptTunnel(session), true, channel);
                channel.setJingleStream(stream);
            } catch (Throwable t) {
                l.warn("fail create JingleStream", t);

                // first, close the stream if it exists
                // can't rely on the channel to clean it up, because apparently
                // either creating it or setting it within the channel failed
                if (stream != null) {
                    stream.close(t);
                }

                // let the channel clean itself up as well
                throw t;
            }

            // bind
            channel.setLocalAddress(_localAddress);
            channel.setRemoteAddress(new JingleAddress(did, jid));
            channel.setBound();
            fireChannelBound(channel, _localAddress);
        } catch (Throwable t) {
            l.warn("fail accept channel", t);

            declineTunnel(client, jid, session, t.getMessage());
            if (channel != null) {
                channel.close(); // will properly close and delete the wrapped stream
            }
        }
    }

    private void declineTunnel(TunnelSessionClient client, Jid jid, SWIGTYPE_p_cricket__Session session, String cause)
    {
        l.warn("decline tunnel j:{} cause:{}", jid.Str(), cause);
        client.DeclineTunnel(session);
    }

    @Override
    public void closeAllAcceptedChannels()
    {
        l.debug("close all accepted channels");

        Map<DID, JingleClientChannel> previouslyAcceptedChannels = Maps.newHashMap(_acceptedChannels);
        for (JingleClientChannel clientChannel : previouslyAcceptedChannels.values()) {
            clientChannel.close();
        }
    }
}
