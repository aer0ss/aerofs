/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.jingle.SignalThread.ISignalThreadListener;
import com.aerofs.j.Jid;
import com.aerofs.j.SWIGTYPE_p_cricket__Session;
import com.aerofs.j.TunnelSessionClient;
import com.google.common.collect.Maps;
import org.jboss.netty.channel.AbstractServerChannel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DefaultServerChannelConfig;
import org.slf4j.Logger;

import java.net.SocketAddress;
import java.util.Map;

import static org.jboss.netty.channel.Channels.fireChannelBound;

public class JingleServerChannel extends AbstractServerChannel implements ISignalThreadListener
{
    private static final Logger l = Loggers.getLogger(AbstractServerChannel.class);
    private volatile JingleAddress _localAddress;
    private final ChannelConfig _config;

    private final Map<DID, JingleClientChannel> _acceptedChannels = Maps.newConcurrentMap();

    /**
     * Creates a new instance.
     *
     * @param factory the factory which created this channel
     * @param pipeline the pipeline which is going to be attached to this channel
     * @param sink the sink which will receive downstream events from the pipeline and send upstream
     * events to the pipeline
     */
    protected JingleServerChannel(ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink,
            SignalThread signalThread)
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
        DID did;
        try {
            did = JingleUtils.jid2did(jid);
        } catch (ExFormatError e) {
            l.warn("eng: incoming connection w/ bogus id. decline: " + jid.Str());
            client.DeclineTunnel(session);
            return;
        }

        l.debug("eng: new channel d:{}", did);

        JingleStream jingleStream = null;
        try {
            ChannelPipeline pipeline = getConfig().getPipelineFactory().getPipeline();
            JingleClientChannel acceptedChannel = new JingleClientChannel(this, getFactory(), pipeline, getPipeline().getSink());
            _acceptedChannels.put(did, acceptedChannel);

            // Bind
            acceptedChannel.setLocalAddress(_localAddress);
            acceptedChannel.setRemoteAddress(new JingleAddress(did, jid));
            jingleStream = new JingleStream(client.AcceptTunnel(session), did, true, acceptedChannel);
            acceptedChannel.setJingleDataStream(jingleStream);
            acceptedChannel.setBound();
            fireChannelBound(acceptedChannel, _localAddress);

        } catch (Exception e) {
            l.warn("j: exception caught while accepting tunnel from {}", did, e);
            if (jingleStream != null) jingleStream.close(e);
        }
    }
}
