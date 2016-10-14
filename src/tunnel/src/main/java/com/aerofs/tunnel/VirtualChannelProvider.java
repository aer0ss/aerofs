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
    private boolean _shutdownCalled = false;
    private Runnable _onShutdownComplete;

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
        if (!_shutdownCalled && c == null && _pipelineFactory != null) {
            try {
                c = new VirtualChannel(_handler, connectionId, _pipelineFactory.getPipeline());
            } catch (Exception e) {
                l.error("failed to create virtual channel for {}", connectionId, e);
                return null;
            }
            l.info("created virtual channel {}:{}", connectionId, c.getId());
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
        if (_connections.size() == 0 && _onShutdownComplete != null) {
            _onShutdownComplete.run();
            _onShutdownComplete = null;
        }
    }

    public void foreach(Function<VirtualChannel, Void> f)
    {
        _connections.values().forEach(f::apply);
    }

    public void clear()
    {
        _connections.clear();
    }

    public boolean isEmpty()
    {
        return _connections.isEmpty();
    }

    public void shutdown(Runnable onShutdownComplete) {
        if (!_shutdownCalled) {
            _shutdownCalled = true;
            // This check is thread-safe as get/remove/shutdown are all called from the io thread
            // of the physical tunnel channel.
            if (_connections.isEmpty()) {
                onShutdownComplete.run();
            } else {
                _onShutdownComplete = onShutdownComplete;
            }
        }
    }
}
