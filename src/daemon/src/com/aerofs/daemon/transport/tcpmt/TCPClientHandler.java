/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.base.Loggers;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.MagicHeader.ExBadMagicHeader;
import com.aerofs.base.ssl.CNameVerificationHandler.CNameListener;
import com.aerofs.lib.log.LogUtil;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;

import java.nio.channels.ClosedChannelException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;


class TCPClientHandler extends SimpleChannelHandler implements CNameListener
{
    private static final Logger l = Loggers.getLogger(TCPClientHandler.class);

    // Hold writes until we are connected to the server
    private final Queue<PendingWrite> _pendingWrites = Queues.newConcurrentLinkedQueue();
    private final Object _writeLock = new Object();

    // This variable is set as soon as we receive the ChannelOpen event and doesn't change after that.
    // In Netty's current threading model, "channelOpen is triggered by the thread that called
    // ChannelFactory.newChannel()" [1], which I believe is the thread that called connect() on
    // ClientBootstrap. Therefore, this variable should never be null EXCEPT in the context of
    // exceptionCaught(), if an exception is thrown by a downstream handler while handling the
    // ChannelOpen event.
    // [1] https://github.com/netty/netty/wiki/Thread-model
    private Channel _channel;

    // This is set once the CNameVerificationHandler successfully verifies the remote peer. We should
    // not send anything down the wire until this is set.
    private volatile DID _did;
    private volatile DID _expectedDid;
    private final AtomicBoolean _disconnected = new AtomicBoolean(false);


    @Override
    public void onPeerVerified(UserID user, DID did)
    {
        _did = did;
    }

    /**
     * Sets the did that we are expecting from the remote peer. Once the cname verification completes,
     * we will match the verified did against this.
     */
    public void setExpectedRemoteDid(DID did)
    {
        _expectedDid = did;
    }

    public void disconnect()
    {
        // Note: though Netty docs says that calling close() several times on a channel is ok, we
        // observe that this actually causes problems with the SslHandler. So we make sure to only
        // call it once
        if (_disconnected.getAndSet(true)) return;

        l.info("client: disconnect from {} {}", _did, _channel);
        _channel.close();
    }

    /**
     * @return the pipeline associated with this handler
     */
    public ChannelPipeline getPipeline()
    {
        return _channel.getPipeline();
    }

    public boolean isConnected()
    {
        // Note (GS): In an ideal world, the CNameVerificationHandler would do some magic so that
        // _channel.isConnected() returns false until the CName is verified. Unfortunately, I
        // haven't found a way to do that. I'm able to delay firing the channelConnected event until
        // the CName is verified, but I can't make _channel.isConnected() lie to you. Therefore, we
        // also check whether we got the DID from the CNameVerificationHandler in order to determine
        // whether we are connected.
        return _channel.isConnected() && _did != null;
    }

    public ListenableFuture<Void> send(byte[][] bytes)
    {
        final UncancellableFuture<Void> future = UncancellableFuture.create();

        synchronized (_writeLock) {
            // If the channel has been closed, fail the request right away
            if (_channel.getCloseFuture().isDone()) {
                future.setException(getCloseReason(_channel));
                return future;
            }

            // Else, either send the request down the wire or enqueue it if we're not connected yet
            if (isConnected()) {
                doWrite(bytes, future);
            } else {
                _pendingWrites.add(new PendingWrite(bytes, future));
            }
        }

        return future;
    }

    private void doWrite(final byte[][] bytes, final UncancellableFuture<Void> future)
    {
        ChannelBuffer buffer = ChannelBuffers.copiedBuffer(bytes);
        final int length = buffer.readableBytes();
        ChannelFuture writeFuture = _channel.write(buffer);

        writeFuture.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture writeFuture)
                    throws Exception
            {
                if (writeFuture.isSuccess()) {
                    l.trace("wrote {} bytes to {}" ,length, _did);
                    future.set(null);
                } else {
                    l.info("write failed ({} bytes to {} {})", length, _did, _channel);
                    future.setException(writeFuture.getCause());
                }
            }
        });
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        _channel = e.getChannel();
        _channel.getCloseFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception
            {
                failPendingWrites(getCloseReason(_channel));
            }
        });

        super.channelOpen(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        // Check that we connected to the peer we intended to.
        // There is a theoretical race condition here if we get the channel connected event before
        // we had the time to set _expectedDid but it should be impossible to happen in practice.
        if (!_expectedDid.equals(_did)) {
            throw new Exception("Peer mismatch. Connected to: " + _did + " expected: " + _expectedDid);
        }

        l.info("client connected to {} {}.", _did, _channel);

        // Send all pending writes now that we are connected
        sendPendingWrites();

        super.channelConnected(ctx, e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception
    {
        // This is the only place where _channel can potentially be null
        if (_channel == null) _channel = e.getChannel();

        l.warn("client: caught ex from: {} {}", _did, _channel, LogUtil.suppress(e.getCause(),
                ExBadMagicHeader.class));

        failPendingWrites(e.getCause());
        disconnect();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        throw new IllegalStateException("client should not receive");
    }

    private void sendPendingWrites()
    {
        // We need to synchronize on _writeLock to ensure proper ordering of writes,
        // otherwise another thread could write in the middle of the pending writes.
        synchronized (_writeLock) {
            PendingWrite pending;
            while ((pending = _pendingWrites.poll()) != null) {
                doWrite(pending.bytes, pending.future);
            }
        }
    }

    private void failPendingWrites(Throwable reason)
    {
        // Note: this may be called while we are iterating over _pendingWrites inside
        // sendPendingWrites()
        synchronized (_writeLock) {
            PendingWrite pending;
            while ((pending = _pendingWrites.poll()) != null) {
                pending.future.setException(reason);
            }
        }
    }

    private static class PendingWrite
    {
        final byte[][] bytes;
        final UncancellableFuture<Void> future;

        PendingWrite(byte[][] bytes, UncancellableFuture<Void> future)
        {
            this.bytes = bytes;
            this.future = future;
        }
    }

    /**
     * Helper method to return the exception that triggered the channel disconnection, or
     * a new ClosedChannelException if no such exception is set.
     */
    private Throwable getCloseReason(Channel channel)
    {
        checkState(channel.getCloseFuture().isDone());

        Throwable reason = channel.getCloseFuture().getCause();
        return (reason != null) ? reason : new ClosedChannelException();
    }
}
