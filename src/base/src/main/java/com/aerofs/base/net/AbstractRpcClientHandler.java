package com.aerofs.base.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.async.UncancellableFuture;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.Iterator;
import java.util.Queue;

import static com.aerofs.base.TimerUtil.getGlobalTimer;
import static com.google.common.collect.Queues.newArrayDeque;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

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
    private static final Logger l = Loggers.getLogger(AbstractRpcClientHandler.class);

    // We use this queue to match queries with replies.
    // Every time we send a query to the server, we create a future and enqueue it here.
    // When we receive a reply, we dequeue the future and set it with the reply.
    // So this only works because the server guarantees to process the requests and send the
    // replies in order.
    private final Queue<UncancellableFuture<byte[]>> _pendingReads = newArrayDeque(); // protected by 'this'

    // Hold writes until we are connected to the server
    private final Queue<byte[]> _pendingWrites = newArrayDeque(); // protected by 'this'
    private final Timer _timer = getGlobalTimer();
    private final long _timeoutDuration;

    // Holds the exception that triggered a disconnection
    // Access to this field must be synchronized on this
    private Throwable _lastException;

    private Channel _channel;

    /**
     * @param timeoutDuration timeout in milliseconds for the request. Set to 0 to have no timeout.
     */
    public AbstractRpcClientHandler(long timeoutDuration)
    {
        _timeoutDuration = timeoutDuration;
    }

    /**
     * Sends data to the server
     * @return a future that will hold the reply
     * This method is called by the auto-generated ServiceStub - you should not call it directly
     */
    public ListenableFuture<byte[]> doRPC(byte[] bytes)
    {
        final UncancellableFuture<byte[]> readFuture = UncancellableFuture.create();

        synchronized (this) {
            if (_channel.getCloseFuture().isDone()) {
                // The channel has been closed. We can fail the request right away.
                readFuture.setException(getCloseReason(_channel));
            } else {
                _pendingReads.add(readFuture);

                // Add a timeout
                if (_timeoutDuration > 0) {
                    _timer.newTimeout(timeout -> {
                        synchronized (AbstractRpcClientHandler.this) {
                            if (_pendingReads.contains(readFuture)) {
                                disconnect(new IOException("request timed out"));
                            }
                        }
                    }, _timeoutDuration, MILLISECONDS);
                }

                // Send the request down the wire or enqueue it if we're not connected yet
                if (_channel.isConnected()) {
                    doWrite(bytes, readFuture);
                } else {
                    _pendingWrites.add(bytes);
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

        // If write failed, drain all requests and close the channel
        writeFuture.addListener(future -> {
            if (!future.isSuccess()) {
                disconnect(future.getCause());
            }
        });
    }

    public void disconnect()
    {
        _channel.disconnect();
    }

    private void disconnect(Throwable reason)
    {
        l.debug("Disconnecting with reason: " + reason);
        drainPendingRequests(reason);
        disconnect();
    }

    public boolean isConnected()
    {
        return _channel.isConnected();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
    {
        UncancellableFuture<byte[]> readFuture;

        synchronized (this) {
            readFuture = _pendingReads.poll();
        }

        // If replyFuture is null, we received a reply with no previous query
        if (readFuture == null) {
            throw new ChannelException("Received an unexpected RPC reply");
        }

        readFuture.set(NettyUtil.toByteArray((ChannelBuffer)e.getMessage()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        if (_channel == null) _channel = e.getChannel();
        disconnect(e.getCause());
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception
    {
        synchronized (this) {
            _channel = e.getChannel();
            _channel.getCloseFuture().addListener(
                    future -> drainPendingRequests(getCloseReason(_channel)));
        }
        super.channelOpen(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception
    {
        synchronized (this) {
            // Flush all pending writes now that we are connected
            Iterator<byte[]> writeIter = _pendingWrites.iterator();
            Iterator<UncancellableFuture<byte[]>> readIter = _pendingReads.iterator();
            while (writeIter.hasNext()) {
                doWrite(writeIter.next(), readIter.next());
            }
            _pendingWrites.clear();
        }

        super.channelConnected(ctx, e);
    }

    private void drainPendingRequests(Throwable reason)
    {
        synchronized (this) {
            for (UncancellableFuture<byte[]> pending : _pendingReads) {
                pending.setException(reason);
            }
            _pendingReads.clear();
            _pendingWrites.clear();
            _lastException = reason;
        }
    }

    private Throwable getCloseReason(Channel channel)
    {
        assert channel.getCloseFuture().isDone();

        synchronized (this) {
            if (_lastException != null) return _lastException;
        }
        Throwable reason = channel.getCloseFuture().getCause();
        return (reason != null) ? reason : new ClosedChannelException();
    }
}
