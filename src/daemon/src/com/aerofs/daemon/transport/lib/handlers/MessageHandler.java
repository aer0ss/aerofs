/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.transport.lib.IChannelData;
import com.aerofs.daemon.transport.lib.TransportDefects;
import com.aerofs.daemon.transport.lib.TransportUtil;
import com.aerofs.lib.SystemUtil;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.aerofs.daemon.transport.lib.TransportUtil.getChannelData;
import static com.aerofs.daemon.transport.lib.TransportUtil.hasValidChannelData;
import static com.aerofs.defects.Defects.newDefect;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jboss.netty.channel.Channels.fireMessageReceived;
import static org.jboss.netty.channel.Channels.write;

public final class MessageHandler extends SimpleChannelHandler
{
    private static final int QUEUE_FLUSH_INTERVAL = (int) (1 * C.SEC);
    private static final int NUM_QUEUE_FLUSH_WAITS = 3;

    //
    // PendingWrite
    //

    private static class PendingWrite
    {
        final byte[][] bytes;
        final ChannelHandlerContext ctx;
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

    //
    // WARNING: NOTE ABOUT CONCURRENCY:
    // ================================
    // ================================
    //   |
    //   |
    //   |
    //   |
    //   V
    // Background:
    // -----------
    //
    // The transport netty pipeline is arranged as follows:
    //
    //     +-----------------------------+       +-----------------------------+
    // <-->| SslHandler (sslHandlerLock) |<-...->|  MessageHandler(writeLock)  |<-->
    //     +-----------------------------+       +-----------------------------+
    //
    // The following requirements hold:
    // 1. We should not send packets out until the CName of the remote peer is verified.
    // 2. We want all outgoing packets to be ordered.
    //
    // The following facts hold:
    // 1. When SSL handshaking completes the SslHandler automatically sends out all packets in its write buffer.
    // 2. The SslHandler holds an internal lock while:
    //    a. writing to the channel
    //    b. closing the channel
    //
    // Problem:
    // --------
    //
    // In the previous iteration of this handler we protected both
    // the channel and the pendingWrites queue using 'writeLock'. This caused a deadlock,
    // for example in the following situation:
    //
    // -----------------------------------------------------------------------------------
    //
    //
    //  >-- Netty I/O Thread (holds sslHandlerLock) -----X (waits to acquire writeLock)
    //
    //     +-----------------------------+       +-----------------------------+
    // <-->| SslHandler (sslHandlerLock) |<-...->|  MessageHandler(writeLock)  |<-->
    //     +-----------------------------+       +-----------------------------+
    //
    // (waits to acquire sslHandlerLock) X----- Application Thread (holds writeLock)--<
    //
    //
    // -----------------------------------------------------------------------------------
    //
    // Fix:
    // ----
    //
    // Concepts:
    //
    // To work around this deadlock I use 3 concepts (unfortunately):
    //
    // 1. A 'pendingWrites' queue, which holds all pending outgoing writes.
    // 2. A 'pendingWritesQueueFlushed' boolean, which indicates whether all
    //    packets from the 'pendingWrites' have been either:
    //      a. failed
    //      b. written
    // 3. A 'writeLock' object that protects access to 'pendingWrites'
    //
    // A caller has to hold 'writeLock' before accessing either 'pendingWrites' or 'pendingWritesQueueFlushed'.
    //
    // Write Operation:
    //
    // If the reference to 'pendingWrites' is _non null_:
    //    1. the caller simply appends to the queue while _holding the lock_.
    // If the reference is _null_:
    //    1. the caller must _wait_ until all pending packets in the queue are flushed
    //       (indicated by pendingWritesQueueFlushed = true), and then only
    //    2. write to the channel while _not holding the lock_.
    //
    // I/O Thread Operation:
    //
    // If the reference to 'pendingWrites' is _non null_:
    //    1. the caller copies the 'pendingQueue' reference to a temporary variable
    //    2. sets the 'pendingQueue' reference to null
    //    3. releases 'writeLock'
    //    4. performs the flush operation (writing down the channel or failing the future)
    //    5. locks 'writeLock'
    //    6. sets 'pendingWritesQueueFlushed = true' to indicate that writers can proceed
    // If the reference is _null_:
    //    1. the I/O thread has nothing to do and noops
    //
    //
    // State Machine:
    //
    //  wait to connect                                            flush                                   flush complete
    //  [queue, false] --(handshake completed/thread closed)-> [null, false] --(pending packets flushed)--> [null, true]

    private final Object writeLock = new Object(); // protect both pendingWrites and pendingWritesQueueFlushed
    private final AtomicBoolean pendingWritesQueueFlushed = new AtomicBoolean(false); // protected by writeLock
    private Queue<PendingWrite> pendingWrites = Queues.newLinkedBlockingDeque(); // protected by writeLock

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

        IChannelData channelData = TransportUtil.getChannelData(e.getChannel());
        TransportMessage message = new TransportMessage((ChannelBuffer) e.getMessage(), channelData.getRemoteDID(), channelData.getRemoteUserID());
        fireMessageReceived(ctx, message);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        Channel channel = ctx.getChannel();

        checkState(TransportUtil.hasValidChannelData(channel), "connnected event fired for %s before peer verified", channel);

        super.channelConnected(ctx, e);

        sendPendingWrites();
    }

    private void sendPendingWrites()
            throws Exception
    {
        Queue<PendingWrite> writesToSend = null;
        PendingWrite pending = null;
        try {
            // remove the write queue
            synchronized (writeLock) {
                checkState(pendingWrites != null); // if the channel is closed, it should happen after the channelConnected event
                writesToSend = pendingWrites;
                pendingWrites = null;
            }

            // write out all the packets in the queue
            while ((pending = writesToSend.poll()) != null) {
                doWrite(pending.bytes, pending.ctx, pending.future);
            }
            pending = null; // reset the final Pending

            // notify any waiting writers that they're now free to write directly to the channel
            pendingWritesQueueFlushed.set(true);
            synchronized (writeLock) {
                writeLock.notifyAll();
            }
        } catch (Exception e) {
            // fail the current pending packet we were writing out
            if (pending != null) {
                pending.future.setFailure(e);
            }

            // fail all packets we didn't write out
            if (writesToSend != null) {
                while ((pending = writesToSend.poll()) != null) {
                    pending.future.setFailure(e);
                }
            }

            // finish off by rethrowing the exception
            throw e;
        }
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        checkArgument(e.getMessage() instanceof byte[][], "invalid message type:%s", e.getMessage().getClass().getSimpleName());

        byte[][] bytes = (byte[][]) e.getMessage();
        ChannelFuture writeFuture = e.getFuture();

        synchronized (writeLock) {
            // the write queue hasn't been flushed yet, so add your packet to it
            if (pendingWrites != null) {
                pendingWrites.add(new PendingWrite(bytes, ctx, writeFuture));
                return; // IMPORTANT: EARLY RETURN
            }
        }

        // it's too late - either someone's in the process
        // of flushing, or all pending packets has been flushed already
        // and we can write directly to the underlying channel
        writeSafely(ctx, bytes, writeFuture);
    }

    private void writeSafely(ChannelHandlerContext ctx, byte[][] bytes, ChannelFuture writeFuture)
            throws InterruptedException
    {
        Channel channel = ctx.getChannel();

        if (!pendingWritesQueueFlushed.get()) {
            for (int i = 0; i < NUM_QUEUE_FLUSH_WAITS && !pendingWritesQueueFlushed.get(); i++) {
                synchronized (writeLock) {
                    writeLock.wait(QUEUE_FLUSH_INTERVAL);
                }
            }

            if (!pendingWritesQueueFlushed.get()) {
                IChannelData channelData = getChannelData(writeFuture.getChannel());
                l.error("{} write queue for {} not flushed after {} ms", channelData.getRemoteDID(), TransportUtil.hexify(channel), QUEUE_FLUSH_INTERVAL * NUM_QUEUE_FLUSH_WAITS);
                channel.close();
                return; // IMPORTANT: EARLY RETURN
            }
        }

        // bail if the channel is closed
        // IMPORTANT: I do this check as late as possible
        if (channel.getCloseFuture().isDone()) {
            writeFuture.setFailure(getDisconnectCause(channel));
            return;
        }

        doWrite(bytes, ctx, writeFuture);
    }

    // IMPORTANT: DO NOT HOLD A LOCK WHILE CALLING THIS METHOD!
    // IT WILL LEAD TO A DEADLOCK, SINCE write() WILL REQUIRE A LOCK ON THE SSLHANDLER WRITE QUEUE
    private void doWrite(byte[][] bytes, ChannelHandlerContext ctx, ChannelFuture future)
    {
        ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(bytes);
        final int length = buffer.readableBytes();

        write(ctx, future, buffer);

        if (l.isTraceEnabled()) {
            future.addListener(new ChannelFutureListener()
            {
                @Override
                public void operationComplete(ChannelFuture writeFuture)
                        throws Exception
                {
                    IChannelData channelData = getChannelData(writeFuture.getChannel());

                    if (writeFuture.isSuccess()) {
                        l.trace("{} wrote {} bytes over {}", channelData.getRemoteDID(), length, TransportUtil.hexify(writeFuture.getChannel()));
                    } else {
                        l.trace("{} fail write of {} bytes on {}", channelData.getRemoteDID(), length, TransportUtil.hexify(writeFuture.getChannel()), writeFuture.getCause());
                    }
                }
            });
        }
    }

    private void failPendingWrites(Throwable cause)
    {
        Queue<PendingWrite> writesToFail;
        try {
            // check if we have packets to flush
            synchronized (writeLock) {
                // remove the write queue
                // this is a signal to writers that someone is working to flush the queue
                if (pendingWrites != null) {
                    writesToFail = pendingWrites;
                    pendingWrites = null;
                } else {
                     // the channel connected a long time ago, or, the packets are currently
                     // being written out and it's too late (they'll be failed when they hit
                     // the bottom of the pipeline)
                    return;
                }
            }

            // iterate over the queue of pending packets and fail all pending writes
            PendingWrite pending;
            while ((pending = writesToFail.poll()) != null) {
                pending.future.setFailure(cause);
            }

            // notify any waiting writers that they're now free to write
            pendingWritesQueueFlushed.set(true);
            synchronized (writeLock) {
                writeLock.notifyAll();
            }

        } catch (Exception e) {
            // AFAICT there's nothing in the above code that
            // should throw during iteration. If there is, that's unexpected,
            // and I really want to know about it
            newDefect(TransportDefects.DEFECT_NAME_THROW_DURING_FAIL_PENDING_WRITES)
                   .setMessage("throw during failing pending writes")
                   .setException(e)
                   .sendSyncIgnoreErrors();

            // kill the system
            SystemUtil.fatal("unexpected exception while failing pending writes");
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
