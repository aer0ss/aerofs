package com.aerofs.tunnel;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.CNameVerificationHandler.CNameListener;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.handler.stream.ChunkedInput;
import org.jboss.netty.handler.timeout.IdleState;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelUpstreamHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;

/**
 * Tunnel protocol handler
 *
 * Message format
 *      - 2 bytes : message size (not including this field) [handled separately]
 *      - 2 bytes : message type
 *      - 4 bytes : connection id
 *      - (message size - 6) bytes : payload
 *
 * To prevent firewalls from closing idle connections, heartbeats are sent every 30s
 * from both side of the tunnel. On each side, if more than 60s pass without receiving
 * a heartbeat the connection is closed, to avoid keeping around zombie connections.
 *
 * Because we want the virtual channels being multiplexed on top of the physical tunnel
 * channel to behave similarly to real channels wrt closing and changing interest ops
 * we also need special signalling packets (MSG_CLOSE, MSG_SUSPEND and MSG_RESUME)
 *
 * The tunnel handler may fragment large payloads before sending them. It is expected that
 * whatever handler are sitting on top of the virtual channel at the other end can handle
 * fragmented messages.
 */
public class TunnelHandler extends IdleStateAwareChannelUpstreamHandler implements CNameListener
{
    public static LengthFieldPrepender newLengthFieldPrepender()
    {
        return new LengthFieldPrepender(LENGTH_FIELD_SIZE);
    }

    public static LengthFieldBasedFrameDecoder newFrameDecoder()
    {
        return new LengthFieldBasedFrameDecoder(MAX_MESSAGE_SIZE, 0,
                LENGTH_FIELD_SIZE, 0, LENGTH_FIELD_SIZE);
    }

    public static IdleStateHandler newIdleStateHandler(Timer timer)
    {
        // send ping after 30s with no write
        // close after 60s with no read
        return new IdleStateHandler(timer, 60, 30, 0, TimeUnit.SECONDS);
    }

    private static final Logger l = Loggers.getLogger(TunnelHandler.class);

    private static final int TYPE_FIELD_SIZE = 2;
    private static final int CONNECTION_FIELD_SIZE = 4;
    private static final int HEADER_SIZE = TYPE_FIELD_SIZE + CONNECTION_FIELD_SIZE;
    private static final int LENGTH_FIELD_SIZE = 2; // bytes
    static final int MAX_MESSAGE_SIZE = 32 * C.KB;
    private static final int MAX_PAYLOAD_SIZE = MAX_MESSAGE_SIZE - HEADER_SIZE - LENGTH_FIELD_SIZE;
    static {
        // Check that the maximum message size is smaller than the maximum number that can be
        // represented using LENGTH_FIELD_SIZE bytes
        checkState(MAX_MESSAGE_SIZE < Math.pow(256, LENGTH_FIELD_SIZE));
    }

    private static final int MSG_BEAT = 0;

    private static final int MSG_CLOSE = 1;
    private static final int MSG_CLOSED = 2;
    private static final int MSG_SUSPEND = 3;
    private static final int MSG_RESUME = 4;
    private static final int MSG_PAYLOAD = 5;

    private static final ChannelBuffer BEAT = ChannelBuffers.wrappedBuffer(new byte[] {0, MSG_BEAT});

    protected Channel _channel;
    protected TunnelAddress _addr;

    private final ITunnelConnectionListener _listener;
    private final VirtualChannelProvider _provider;

    /**
     * @param pipelineFactory if non-null, auto-generate virtual channel with the given pipeline
     * upon receiving incoming messages for a new connection id.
     */
    public TunnelHandler(ITunnelConnectionListener listener,
            @Nullable ChannelPipelineFactory pipelineFactory)
    {
        _listener = listener;
        _provider = new VirtualChannelProvider(this, pipelineFactory);
    }

    public TunnelAddress address()
    {
        return _addr;
    }

    @Override
    public String toString()
    {
        return "Tunnel(" + _addr + ", " + _channel + ", " + _provider + ")";
    }

    @Override
    public void onPeerVerified(@Nonnull UserID user, @Nonnull DID did)
    {
        checkState(_addr == null);
        _addr = new TunnelAddress(user, did);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        l.warn("tunnel exception {} ", this, BaseLogUtil.suppress(e.getCause(),
                ClosedChannelException.class, SocketException.class,
                UnresolvedAddressException.class));
        if (_channel != null && _channel.isConnected()) _channel.close();
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception
    {
        if (e instanceof ShutdownEvent) {
            if (_channel != null && _channel.isConnected()) {
                _provider.shutdown(() -> _channel.close());
            }
        } else {
            super.handleUpstream(ctx, e);
        }
    }

    @SuppressWarnings("fallthrough")
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) throws IOException
    {
        ChannelBuffer buf = (ChannelBuffer)me.getMessage();
        if (buf.readableBytes() < TYPE_FIELD_SIZE) {
            throw new ProtocolException("tunnel message too small: " + buf.readableBytes());
        }

        short type = buf.readShort();

        // heartbeat reception
        if (type == MSG_BEAT) {
            l.debug("tunnel beat recv {}", this);
            return;
        }

        if (buf.readableBytes() < CONNECTION_FIELD_SIZE) {
            throw new ProtocolException("tunnel message too small: " + buf.readableBytes());
        }

        int connectionId = buf.readInt();

        VirtualChannel client = _provider.get(connectionId);
        if (client == null || !client.isConnected()) {
            // any messages already in-flight at the time the connection is closed
            // need to be discarded
            l.warn("discard incoming tunnel message {} {}", type, connectionId);
            return;
        }

        switch (type) {
        case MSG_CLOSE:
            l.info("tunnel close msg {}", this);
            writeMsg(MSG_CLOSED, connectionId);
            // noinspection fallthrough
        case MSG_CLOSED:
            _provider.remove(connectionId);
            client.fireDisconnected();
            break;
        case MSG_SUSPEND:
            client.makeWritable(false);
            break;
        case MSG_RESUME:
            client.makeWritable(true);
            break;
        case MSG_PAYLOAD:
            //l.info("payload msg on {}: {}", _channel, buf.readableBytes());
            Channels.fireMessageReceived(client, buf.slice(), null);
            break;
        default:
            throw new ProtocolException("invalid tunnel message type: " + type);
        }
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
    {
        checkState(_addr != null);
        checkState(_channel == null);
        _channel = ctx.getChannel();
        checkState(_channel != null);
        l.info("tunnel connect {}", this);
        if (_listener != null) _listener.tunnelOpen(_addr, this);
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e)
    {
        l.debug("physical channel interest changed {} {}", _channel.getInterestOps(), this);
        _provider.foreach(c -> {
            Channels.fireChannelInterestChanged(c);
            return null;
        });
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
    {
        if (_channel == null) return;
        checkState(_addr != null);
        l.info("tunnel disconnect {}", this);
        _provider.foreach(c -> {
            c.fireDisconnected();
            return null;
        });
        _provider.clear();
        if (_listener != null) _listener.tunnelClosed(_addr, this);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
    {
        l.info("tunnel close {}", this);
        checkState(_provider.isEmpty());
    }

    @Override
    public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e)
    {
        if (e.getState() == IdleState.READER_IDLE) {
            l.warn("tunnel beat missed {} {}", e.getChannel().isReadable(), this);
            e.getChannel().close();
        } else if (e.getState() == IdleState.WRITER_IDLE) {
            if (_addr == null) {
                l.warn("cname handshake timeout {}", this);
                // cname handshake not completed in 30s, nothing
                // good can possibly come out of this...
                e.getChannel().close();
            } else {
                l.debug("tunnel beat send {} {}", e.getChannel().isWritable(), this);
                e.getChannel().write(BEAT);
            }
        }
    }

    /**
     * Write a message having reached the sink of the virtual channel to the physical channel
     */
    void onWrite(VirtualChannel virtualChannel, Object message, ChannelFuture future)
    {
        if (_channel.isConnected()) {
            final ChannelBuffer payload = (ChannelBuffer)message;
            final ChannelBuffer header = ChannelBuffers.buffer(HEADER_SIZE);
            header.writeShort(MSG_PAYLOAD);
            header.writeInt(virtualChannel.getConnectionId());
            final long total = payload.readableBytes() + header.readableBytes();

            // failure to reconstruct WriteCompletion events would prevent IdleState handler from
            // correctly detecting write timeouts (i.e. successful writes would never reset the
            // timeout)
            _channel.write(new Fragmenter(header, payload))
                    .addListener(new ChannelFutureProgressListener() {
                        long written;
                        @Override
                        public void operationProgressed(ChannelFuture cf, long n, long w, long t) {
                            written = w;
                            future.setProgress(n, w, t);
                            Channels.fireWriteComplete(virtualChannel, n);
                        }

                        @Override
                        public void operationComplete(ChannelFuture cf) throws Exception {
                            if (cf.isSuccess()) {
                                future.setSuccess();
                                Channels.fireWriteComplete(virtualChannel, total - written);
                            } else {
                                future.setFailure(cf.getCause());
                            }
                        }
                    });
        } else {
            l.warn("ignore write {} -> {}", virtualChannel, this);
            future.setFailure(new ClosedChannelException());
        }
    }

    private ChannelFuture writeMsg(int type, int connectionId) throws ClosedChannelException
    {
        if (!_channel.isConnected()) throw new ClosedChannelException();

        final ChannelBuffer message = ChannelBuffers.buffer(HEADER_SIZE);
        message.writeShort(type);
        message.writeInt(connectionId);
        return _channel.write(message);
    }

    void onReadabilityChanged(final VirtualChannel virtualChannel, boolean suspend,
            final ChannelFuture future)
    {
        try {
            writeMsg(suspend ? MSG_SUSPEND : MSG_RESUME, virtualChannel.getConnectionId());
            future.setSuccess();
            Channels.fireChannelInterestChanged(virtualChannel);
        } catch (ClosedChannelException e) {
            l.warn("ignore write {} -> {}", virtualChannel, this);
            future.setFailure(e);
        }
    }

    /**
     * Handle disconnection of virtual channel
     */
    void onDisconnect(VirtualChannel virtualChannel, ChannelFuture future)
    {
        try {
            writeMsg(MSG_CLOSE, virtualChannel.getConnectionId());
            virtualChannel.getCloseFuture().addListener(new ChannelFutureNotifier(future));

            // fire when receiving ack (MSG_CLOSED)
            // tunnelChannel.fireDisconnected();
        } catch (ClosedChannelException e) {
            l.warn("ignore disconnect {} -> {}", virtualChannel, this);
            future.setSuccess();
        }
    }

    /**
     * Create a new virtual channel, using netty channel id as connection id
     *
     * TODO: use a connection id generation scheme independent of Netty
     */
    public Channel newVirtualChannel(ChannelPipeline pipeline)
    {
        if (!_channel.isConnected()) {
            l.error("cannot create virtual channel: tunnel closed {}", this);
            return null;
        }
        final VirtualChannel c = new VirtualChannel(this, pipeline);
        _provider.put(c);
        Channels.fireChannelOpen(c);
        // execute in I/O thread to respect Netty's threading model
        c.getPipeline().execute(() -> Channels.fireChannelConnected(c, _addr));
        return c;
    }

    /**
     * Helper class to auto-fragment large payloads
     */
    private static class Fragmenter implements ChunkedInput
    {
        private final ChannelBuffer _header;
        private final ChannelBuffer _payload;

        Fragmenter(ChannelBuffer header, ChannelBuffer payload)
        {
            _header = header;
            _payload = payload;
        }

        @Override
        public boolean hasNextChunk() throws Exception
        {
            return _payload.readableBytes() > 0;
        }

        @Override
        public Object nextChunk() throws Exception
        {
            return ChannelBuffers.wrappedBuffer(_header,
                    _payload.readSlice(Math.min(MAX_PAYLOAD_SIZE, _payload.readableBytes())));
        }

        @Override
        public boolean isEndOfInput() throws Exception
        {
            return _payload.readableBytes() == 0;
        }

        @Override
        public void close() throws Exception
        {

        }
    }
}
