package com.aerofs.lib.ritual;

import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.proto.Common;
import com.aerofs.proto.Ritual.RitualServiceStub.RitualServiceStubCallbacks;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

public class RitualClientHandler extends SimpleChannelHandler implements RitualServiceStubCallbacks
{
    // We use this queue to match queries with replies.
    // Every time we send a query to the server, we create a future and enqueue it here.
    // When we receive a reply, we dequeue the future and set it with the reply.
    // So this only works because the server guarantees to process the requests and send the
    // replies in order.
    private final Queue<UncancellableFuture<byte[]>> _pendingReads
        = new ArrayDeque<UncancellableFuture<byte[]>>();

    // Hold writes until we are connected to the server
    private final Queue<byte[]> _pendingWrites = new ArrayDeque<byte[]>();
    private Channel _channel;
    private Throwable _lastException = null;

    /**
     * Sends data to the ritual server
     * @return a future that will hold the reply
     * This method is called by the auto-generated RitualServiceStub - you should not call it directly
     */
    @Override
    public ListenableFuture<byte[]> doRPC(byte[] bytes)
    {
        final UncancellableFuture<byte[]> readFuture = UncancellableFuture.create();
        synchronized (this) {
            if (!_channel.isOpen()) {
                // The channel has been closed. We can fail the request right away.
                Throwable e = _lastException == null ? new IOException("connection closed") :
                    _lastException;
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
            public void operationComplete(ChannelFuture channelFuture) throws Exception
            {
                if (!channelFuture.isSuccess()) {
                    readFuture.setException(new ChannelException("Writing to the daemon failed",
                            channelFuture.getCause()));
                    _pendingReads.remove(readFuture);
                }
            }
        });
    }

    protected void disconnect()
    {
        _channel.disconnect();
    }

    @Override
    public Throwable decodeError(Common.PBException pbe)
    {
        return Exceptions.fromPB(pbe);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
    {
        final UncancellableFuture<byte[]> readFuture = _pendingReads.poll();

        // If replyFuture is null, we received a reply with no previous query
        if (readFuture == null) {
            throw new ChannelException("Received an unexpected reply from the daemon.");
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
        _channel = e.getChannel();
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
        for (UncancellableFuture<byte[]> pending : _pendingReads) {
            pending.setException(reason);
        }
        _pendingReads.clear();
        _pendingWrites.clear();
    }
}
