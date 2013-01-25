package com.aerofs.base.net;

import com.aerofs.base.async.UncancellableFuture;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;

import java.nio.channels.ClosedChannelException;
import java.util.Queue;

import static com.google.common.collect.Queues.newArrayDeque;

/**
 * This class is a generic handler implementation for our protobuf rpc services.
 * It implements the doRPC() method needed by all protobuf services. This implementation will
 * queue up rpc request until we are connected to the server (which means that clients can start
 * querying the server without having to wait for the connection to succeed)
 *
 * For an example on how to use it, look at RitualClientHandler or MobileRpcClientHandler
 */
public class AbstractRpcClientHandler extends SimpleChannelHandler
{
    // We use this queue to match queries with replies.
    // Every time we send a query to the server, we create a future and enqueue it here.
    // When we receive a reply, we dequeue the future and set it with the reply.
    // So this only works because the server guarantees to process the requests and send the
    // replies in order.
    private final Queue<UncancellableFuture<byte[]>> _pendingReads = newArrayDeque();

    // Hold writes until we are connected to the server
    private final Queue<byte[]> _pendingWrites = newArrayDeque();
    private Channel _channel;
    private Throwable _lastException = null;

    // true after the channel has been closed. We need this to distinguish from the case where the
    // channel hasn't been opened yet.
    private volatile boolean _isClosed;

    // TODO (GS): Add a timeout mechanism

    /**
     * Sends data to the server
     * @return a future that will hold the reply
     * This method is called by the auto-generated ServiceStub - you should not call it directly
     */
    public ListenableFuture<byte[]> doRPC(byte[] bytes)
    {
        final UncancellableFuture<byte[]> readFuture = UncancellableFuture.create();
        synchronized (this) {
            if (_isClosed) {
                // The channel has been closed. We can fail the request right away.
                Throwable e = _lastException == null ? new ClosedChannelException() : _lastException;
                readFuture.setException(e);
            } else {
                _pendingReads.add(readFuture);

                if (!_channel.isConnected()) {
                    _pendingWrites.add(bytes);
                } else {
                    doWrite(bytes, readFuture);
                }
            }
        }

        return readFuture;
    }

    /**
     * Send the bytes to the server
     * The caller must ensure that the channel is connected
     * If the write fails, the exception will be set on the readFuture, and it will be removed from
     * the pending reads queue.
     */
    private void doWrite(byte[] bytes, final UncancellableFuture<byte[]> readFuture)
    {
        assert(readFuture != null);

        ChannelBuffer buffer = ChannelBuffers.copiedBuffer(bytes);
        ChannelFuture writeFuture = _channel.write(buffer);

        // If write failed, dequeue and fail the read future
        writeFuture.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception
            {
                if (!future.isSuccess()) {
                    readFuture.setException(future.getCause());
                    synchronized (AbstractRpcClientHandler.this) {
                        _pendingReads.remove(readFuture);
                    }
                }
            }
        });
    }

    public void disconnect()
    {
        _channel.disconnect();
    }

    public boolean isConnected()
    {
        return _channel.isConnected();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
    {
        final UncancellableFuture<byte[]> readFuture = _pendingReads.poll();

        // If replyFuture is null, we received a reply with no previous query
        if (readFuture == null) {
            throw new ChannelException("Received an unexpected RPC reply");
        }

        ChannelBuffer buf = (ChannelBuffer) e.getMessage();
        readFuture.set(buf.array());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        _lastException = e.getCause();
        drainPendingRequests(e.getCause());
        e.getChannel().close();
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception
    {
        synchronized (this) {
            _channel = e.getChannel();
        }
        super.channelOpen(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception
    {
        synchronized (this) {
            for (byte[] bytes : _pendingWrites) {
                doWrite(bytes, _pendingReads.peek());
            }
            _pendingWrites.clear();
        }

        super.channelConnected(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception
    {
        // Fail all pending reads
        drainPendingRequests(new ChannelException("Connection to the daemon closed"));
        super.channelClosed(ctx, e);
    }

    private void drainPendingRequests(Throwable reason)
    {
        synchronized (this) {
            for (UncancellableFuture<byte[]> pending : _pendingReads) {
                pending.setException(reason);
            }
            _pendingReads.clear();
            _pendingWrites.clear();
            _isClosed = true;
        }
    }
}
