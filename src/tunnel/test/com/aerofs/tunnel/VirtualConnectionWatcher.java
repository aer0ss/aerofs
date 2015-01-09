/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.tunnel;

import com.aerofs.base.Loggers;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Future;

import static org.junit.Assert.fail;

/**
 * Helper class to keep track of virtual connections and generate Futures for various
 * expected events
 */
public class VirtualConnectionWatcher extends ConnectionWatcher<Channel>
{
    private static final Logger l = Loggers.getLogger(VirtualConnectionWatcher.class);

    private class Watcher
    {
        Queue<ChannelBuffer> buffers = Queues.newLinkedBlockingQueue();
        Queue<SettableFuture<ChannelBuffer>> readFutures = Queues.newLinkedBlockingQueue();

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
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        {
            l.error("Exception in virtual channel {}" + ctx.getChannel(), e.getCause());
            // TODO: ideally fail fast, unfortunately JUnit fail() uses exception
            // and Netty doesn't like exceptionCaught rethrowing...
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
        {
            l.info("connected {}", ctx.getChannel());
            connected(ctx.getChannel());
            synchronized (_watchers) {
                Preconditions.checkState(_watchers.put(ctx.getChannel(),
                        new Watcher(ctx.getChannel().isWritable())) == null);
                // verifier may already be waiting for watcher, wake if needed
                _watchers.notifyAll();
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
            final boolean writable = e.getChannel().isWritable();
            final SettableFuture<Boolean> future;
            synchronized (_watchers) {
                Watcher w = _watchers.get(ctx.getChannel());
                Preconditions.checkNotNull(w);
                if (w.writable == writable)  {
                    l.info("ignoring interest change with no impact on writability");
                    return;
                }
                w.writable = writable;
                if (w.interestFuture == null) return;
                future = w.interestFuture;
                w.interestFuture = null;
            }
            // must set future outside of synchronized block
            future.set(writable);
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
                if (!msg.readable()) {
                    l.warn("empty message");
                    return;
                }
                l.info("message {}: {}", ctx.getChannel(), msg.readableBytes());
                future = w.readFutures.poll();
                if (future == null) w.buffers.add(ChannelBuffers.copiedBuffer(msg));
            }
            if (future != null) future.set(msg);
        }
    };

    Future<ChannelBuffer> messageReceived(Channel c)
    {
        synchronized (_watchers) {
            Watcher w = get(c);
            ChannelBuffer msg = w.buffers.poll();
            if (msg != null) {
                l.info("read {}", c);
                return Futures.immediateFuture(msg);
            } else {
                l.info("wait {}", c);
                SettableFuture<ChannelBuffer> future = SettableFuture.create();
                w.readFutures.add(future);
                return future;
            }
        }
    }

    public Future<Boolean> interestChanged(Channel c)
    {
        synchronized (_watchers) {
            Watcher w = get(c);
            Preconditions.checkState(w.interestFuture == null);
            return (w.interestFuture = SettableFuture.create());
        }
    }

    private @Nonnull Watcher get(Channel c)
    {
        Watcher w;
        while ((w = _watchers.get(c)) == null) {
            try {
                _watchers.wait();
            } catch (InterruptedException e) {
                fail();
            }
        }
        return w;
    }
}
