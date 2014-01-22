/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.tunnel;

import com.aerofs.base.Loggers;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentMap;

/**
 * Keep track of all virtual channel associated with a physical tunnel connection
 */
public class VirtualChannelProvider
{
    private static final Logger l = Loggers.getLogger(VirtualChannelProvider.class);

    private final TunnelHandler _handler;
    private final @Nullable ChannelPipelineFactory _pipelineFactory;
    private final ConcurrentMap<Integer, VirtualChannel> _connections = Maps.newConcurrentMap();

    public VirtualChannelProvider(TunnelHandler handler,
            @Nullable ChannelPipelineFactory pipelineFactory)
    {
        _handler = handler;
        _pipelineFactory = pipelineFactory;
    }

    @Override
    public String toString()
    {
        return "vc:" + _connections.size();
    }

    public VirtualChannel get(int connectionId)
    {
        VirtualChannel c = _connections.get(connectionId);
        if (c == null && _pipelineFactory != null) {
            l.info("creating virtual channel for {}", connectionId);
            try {
                c = new VirtualChannel(_handler, connectionId, _pipelineFactory.getPipeline());
            } catch (Exception e) {
                l.error("channel creation failed", e);
                return null;
            }
            Preconditions.checkNotNull(c);
            Preconditions.checkState(_connections.put(connectionId, c) == null);
            Channels.fireChannelOpen(c);
            Channels.fireChannelConnected(c, _handler._addr);
        }
        return c;
    }

    public void put(VirtualChannel c)
    {
        Preconditions.checkState(_connections.put(c.getConnectionId(), c) == null);
    }

    public void remove(int connectionId)
    {
        _connections.remove(connectionId);
    }

    public void foreach(Function<VirtualChannel, Void> f)
    {
        for (VirtualChannel c : _connections.values()) f.apply(c);
    }

    public void clear()
    {
        _connections.clear();
    }

    public boolean isEmpty()
    {
        return _connections.isEmpty();
    }
}
