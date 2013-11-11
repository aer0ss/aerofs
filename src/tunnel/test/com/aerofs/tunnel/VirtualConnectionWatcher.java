/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.tunnel;

import com.aerofs.base.Loggers;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.Future;

/**
 * Helper class to keep track of virtual connections and generate Futures for various
 * expected events
 */
public class VirtualConnectionWatcher extends ConnectionWatcher<Channel>
{
    private static final Logger l = Loggers.getLogger(VirtualConnectionWatcher.class);

    private class Watcher
    {
        ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();
        SettableFuture<ChannelBuffer> readFuture;

        boolean writable;
        SettableFuture<Boolean> interestFuture;

        Watcher(boolean writable)
        {
            this.writable = writable;
        }
    }

    private final Map<Channel, Watcher> _watchers = Maps.newHashMap();

    final ChannelHandler handler = new SimpleChannelUpstreamHandler() {
        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
        {
            l.info("connected {}", ctx.getChannel());
            connected(ctx.getChannel());
            synchronized (_watchers) {
                Preconditions.checkState(_watchers.put(ctx.getChannel(),
                        new Watcher(ctx.getChannel().isWritable())) == null);
            }
        }

        @Override
        public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
        {
            l.info("disconnected {}", ctx.getChannel());
            disconnected(ctx.getChannel());
            synchronized (_watchers) {
                Preconditions.checkNotNull(_watchers.remove(ctx.getChannel()));
            }
        }

        @Override
        public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e)
        {
            final boolean writable;
            final SettableFuture<Boolean> future;
            synchronized (_watchers) {
                Watcher w = _watchers.get(ctx.getChannel());
                Preconditions.checkNotNull(w);
                w.writable = e.getChannel().isWritable();
                if (w.interestFuture != null) {
                    future = w.interestFuture;
                    writable = w.writable;
                    w.interestFuture = null;
                } else {
                    future = null;
                    writable = false;
                }
            }
            if (future != null) future.set(writable);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent me)
        {
            final ChannelBuffer msg;
            final SettableFuture<ChannelBuffer> future;
            synchronized (_watchers) {
                Watcher w = _watchers.get(ctx.getChannel());
                Preconditions.checkNotNull(w);
                msg = (ChannelBuffer)me.getMessage();
                l.info("message {}: {}", ctx.getChannel(), msg.readableBytes());
                if (w.readFuture != null && msg.readableBytes() > 0) {
                    future = w.readFuture;
                    w.readFuture = null;
                } else {
                    future = null;
                    w.buffer.writeBytes(msg);
                }
            }
            if (future != null) future.set(msg);
        }
    };

    Future<ChannelBuffer> messageReceived(Channel c)
    {
        synchronized (_watchers) {
            Watcher w = _watchers.get(c);
            Preconditions.checkNotNull(w);
            if (w.buffer.readableBytes() > 0) {
                Future<ChannelBuffer> f = Futures.immediateFuture(w.buffer);
                w.buffer = ChannelBuffers.dynamicBuffer();
                return f;
            } else {
                Preconditions.checkState(w.readFuture == null);
                return (w.readFuture = SettableFuture.create());
            }
        }
    }

    public Future<Boolean> interestChanged(Channel c)
    {
        synchronized (_watchers) {
            Watcher w = _watchers.get(c);
            Preconditions.checkNotNull(w);
            Preconditions.checkState(w.interestFuture == null);
            return (w.interestFuture = SettableFuture.create());
        }
    }
}
