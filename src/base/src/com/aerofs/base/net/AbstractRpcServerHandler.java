/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.net;

import com.aerofs.base.Loggers;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;

public abstract class AbstractRpcServerHandler extends SimpleChannelUpstreamHandler
{
    private static final Logger l = Loggers.getLogger(AbstractRpcServerHandler.class);

    protected abstract ListenableFuture<byte[]> react(byte[] data);

    ListenableFuture<?> _latest = Futures.immediateFuture(null);

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, MessageEvent e) throws Exception
    {
        try {
            ChannelBuffer cb = (ChannelBuffer)e.getMessage();
            byte[] message = NettyUtil.toByteArray(cb);
            final Channel channel = e.getChannel();

            final SettableFuture<?> next = SettableFuture.create();
            final ListenableFuture<?> previous = _latest;
            _latest = next;

            final ListenableFuture<byte[]> future = react(message);

            previous.addListener(() -> Futures.addCallback(future, new FutureCallback<byte[]>()
            {
                @Override
                public void onSuccess(byte[] response)
                {
                    try {
                        channel.write(ChannelBuffers.wrappedBuffer(response));
                    } finally {
                        next.set(null);
                    }
                }

                @Override
                public void onFailure(Throwable throwable)
                {
                    next.setException(throwable);
                    l.warn("Received an exception from the reactor. This should never happen.");
                    Channels.fireExceptionCaught(ctx.getChannel(), throwable);
                }
            }), MoreExecutors.sameThreadExecutor());

        } catch (Exception ex) {
            l.warn("RPC error", ex);
            Channels.fireExceptionCaught(ctx.getChannel(), ex);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception
    {
        l.warn("exception caught", e.getCause());
        // Close the connection when an exception is raised
        e.getChannel().close();
    }
}
