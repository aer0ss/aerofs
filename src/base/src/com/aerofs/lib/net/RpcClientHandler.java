/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.net;

import com.aerofs.lib.Loggers;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;

import java.nio.channels.ClosedChannelException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RpcClientHandler extends SimpleChannelUpstreamHandler
{
    private static final Logger l = Loggers.getLogger(RpcClientHandler.class);

    private ChannelHandlerContext _ctx;
    private final Queue<SettableFuture<byte[]>> _queue = new ConcurrentLinkedQueue<SettableFuture<byte[]>>();

    public ListenableFuture<byte[]> doRPC(byte[] data)
    {
        final SettableFuture<byte[]> rpcFuture = SettableFuture.create();
        doRPC(data, rpcFuture);
        return rpcFuture;
    }

    public void doRPC(byte[] data, final SettableFuture<byte[]> rpcFuture)
    {
        l.info("Doing the RPC");
        if (!_ctx.getChannel().isConnected()) {
            l.info("not connected, sertting closed channel exception. we should wait intead");
            rpcFuture.setException(new ClosedChannelException());
            return;
        }
        _queue.add(rpcFuture);
        _ctx.getChannel().write(
                ChannelBuffers.wrappedBuffer(data)).addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future)
                    throws Exception
            {
                if (!future.isSuccess()) {
                    l.info("ChannelFuture not successful");
                    rpcFuture.setException(future.getCause());
                }
            }
        });
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        l.info("Channel open");
        _ctx = ctx;
        super.channelOpen(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        l.info("Message received");
        ChannelBuffer cb = (ChannelBuffer)e.getMessage();
        byte[] data = cb.array();
        _queue.remove().set(data);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception
    {
        l.info("Exception caught: " + e.getCause());
        SettableFuture<byte[]> future;
        while ((future = _queue.poll()) != null) {
            future.setException(e.getCause());
        }
        e.getChannel().close();
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        l.info("Channel closed");
        Exception ex = null;
        SettableFuture<byte[]> future;
        while ((future = _queue.poll()) != null) {
            if (ex == null) ex = new ClosedChannelException();
            future.setException(ex);
        }
        super.channelClosed(ctx, e);
    }
}
