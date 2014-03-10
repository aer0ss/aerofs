/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.transport.lib.ChannelDataUtil;
import com.aerofs.daemon.transport.lib.IChannelData;
import com.google.common.collect.Queues;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import static com.aerofs.daemon.transport.lib.ChannelDataUtil.getChannelData;
import static com.aerofs.daemon.transport.lib.ChannelDataUtil.hasValidChannelData;
import static com.aerofs.daemon.transport.lib.ChannelDataUtil.isChannelConnected;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jboss.netty.channel.Channels.fireMessageReceived;
import static org.jboss.netty.channel.Channels.write;

public final class MessageHandler extends SimpleChannelHandler
{
    //
    // PendingWrite
    //

    private static class PendingWrite
    {
        final byte[][] bytes;
        private final ChannelHandlerContext ctx;
        final ChannelFuture future;

        PendingWrite(byte[][] bytes, ChannelHandlerContext ctx, ChannelFuture future)
        {
            this.bytes = bytes;
            this.ctx = ctx;
            this.future = future;
        }
    }

    //
    // MessageHandler
    //

    private static final Logger l = Loggers.getLogger(MessageHandler.class);

    // hold writes until we are connected to the remote peer
    private final Queue<PendingWrite> pendingWrites = Queues.newConcurrentLinkedQueue();
    private final Object writeLock = new Object(); // FIXME (AG): the use of a lock causes a deadlock with the SSLHandler

    // disconnection reason
    // explicitly set by the user, or, if an exception was thrown in the pipeline
    private final AtomicReference<Throwable> disconnectReason = new AtomicReference<Throwable>(null); // set once, to the first exception thrown, or first disconnection reason

    public boolean setDisconnectReason(Throwable cause)
    {
        return disconnectReason.compareAndSet(null, cause);
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        final Channel channel = e.getChannel();

        channel.getCloseFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception
            {
                failPendingWrites(getDisconnectCause(channel));
            }
        });

        super.channelOpen(ctx, e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception
    {
        disconnectReason.compareAndSet(null, e.getCause());
        super.exceptionCaught(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws IOException
    {
        Channel channel = ctx.getChannel();

        checkState(hasValidChannelData(channel), "message received for %s before peer verified", channel);

        IChannelData channelData = ChannelDataUtil.getChannelData(e);
        TransportMessage message = new TransportMessage((ChannelBuffer) e.getMessage(), channelData.getRemoteDID(), channelData.getRemoteUserID());
        fireMessageReceived(ctx, message);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        Channel channel = ctx.getChannel();

        checkState(ChannelDataUtil.hasValidChannelData(channel), "connnected event fired for %s before peer verified", channel);

        sendPendingWrites();
        super.channelConnected(ctx, e);
    }

    private void sendPendingWrites()
    {
        // We need to synchronize on writeLock to ensure proper ordering of writes,
        // otherwise another thread could write in the middle of the pending writes.
        synchronized (writeLock) {
            PendingWrite pending;
            while ((pending = pendingWrites.poll()) != null) {
                doWrite(pending.bytes, pending.ctx, pending.future);
            }
        }
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        checkArgument(e.getMessage() instanceof byte[][], "invalid message type:%s", e.getMessage().getClass().getSimpleName());

        Channel channel = e.getChannel();
        byte[][] bytes = (byte[][]) e.getMessage();
        ChannelFuture writeFuture = e.getFuture();

        synchronized (writeLock) {
            // If the channel has been closed, fail the request right away
            if (channel.getCloseFuture().isDone()) {
                writeFuture.setFailure(getDisconnectCause(channel));
                return;
            }

            // Else, either send the request down the wire or enqueue it if we're not connected yet
            if (isChannelConnected(channel)) {
                doWrite(bytes, ctx, writeFuture);
            } else {
                pendingWrites.add(new PendingWrite(bytes, ctx, writeFuture));
            }
        }
    }

    private void doWrite(byte[][] bytes, ChannelHandlerContext ctx, ChannelFuture future)
    {
        ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(bytes);
        final int length = buffer.readableBytes();

        write(ctx, future, buffer);

        future.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture writeFuture)
                    throws Exception
            {
                IChannelData channelData = getChannelData(writeFuture.getChannel());

                if (writeFuture.isSuccess()) {
                    l.trace("{} wrote {} bytes", channelData.getRemoteDID(), length);
                } else {
                    l.trace("{} fail write of {} bytes on {}", channelData.getRemoteDID(), length, writeFuture.getChannel(),
                            writeFuture.getCause());
                }
            }
        });
    }

    private void failPendingWrites(Throwable reason)
    {
        // Note: this may be called while we are iterating over pendingWrites inside
        // sendPendingWrites()
        synchronized (writeLock) {
            PendingWrite pending;
            while ((pending = pendingWrites.poll()) != null) {
                pending.future.setFailure(reason);
            }
        }
    }

    /**
     * Helper method to return the exception that triggered the channel disconnection, or
     * a new {@link ChannelException} if no such exception is set.
     */
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    private Throwable getDisconnectCause(Channel channel)
    {
        checkState(channel.getCloseFuture().isDone());

        if (disconnectReason.get() != null) {
            return disconnectReason.get();
        } else if (channel.getCloseFuture().getCause() != null) {
            return channel.getCloseFuture().getCause();
        } else {
            return new ChannelException("channel closed");
        }
    }
}
