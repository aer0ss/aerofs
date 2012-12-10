/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.net;

import com.aerofs.lib.Loggers;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.LifeCycleAwareChannelHandler;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

/**
 * Channel handler to negotiate a Zephyr connection.
 *
 * The client should wait for the getReceiveLocalZidFuture() to finish and then send the zid to
 * its peer via some outside mechanism. When the client receives the peer's zid, it should call
 * the setRemoteZid() method. Only when both of these are done does the channel finish
 * "connecting", at which point the two sides can communicate as though the channel were a
 * normal TCP connection.
 *
 * Caveat: Zephyr (like netty) doesn't support TCP's half-close functionality, so the application
 * layer has to implement its own half-close mechanism if needed.
 */
public class ZephyrPipeHandler extends SimpleChannelHandler
{
    private static final Logger l = Loggers.getLogger(ZephyrPipeHandler.class);

    private int _localZid = ZEPHYR_INVALID_CHAN_ID;
    private int _remoteZid = ZEPHYR_INVALID_CHAN_ID;

    private ChannelFuture _upstreamConnected;

    private ChannelHandlerContext _ctx;
    private ChannelFuture _connected;
    private ChannelFuture _recvLocalZid;
    private ChannelFuture _sendRemoteZid;

    public int getLocalZid()
    {
        return _localZid;
    }

    public int getRemoteZid()
    {
        return _remoteZid;
    }

    public ChannelFuture getSendRemoteZidFuture()
    {
        return _sendRemoteZid;
    }

    public ChannelFuture getReceiveLocalZidFuture()
    {
        return _recvLocalZid;
    }

    public ChannelFuture setRemoteZid(final int zid)
    {
        sendRemoteZid(_ctx, zid);
        return _sendRemoteZid;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        Channel channel = ctx.getChannel();
        _ctx = ctx;
        _connected = Channels.future(channel);
        _recvLocalZid = Channels.future(channel);
        _sendRemoteZid = Channels.future(channel);
        _connected.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future)
                    throws Exception
            {
                if (!future.isSuccess()) {
                    _recvLocalZid.setFailure(future.getCause());
                    _sendRemoteZid.setFailure(future.getCause());
                    future.getChannel().close();
                    return;
                }
            }
        });
        ctx.getPipeline().addBefore(ctx.getName(), ctx.getName() + "Decoder",
                new ZephyrInitDecoder());
        super.channelOpen(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        Exception cause = new ClosedChannelException();
        _connected.setFailure(cause);
        _recvLocalZid.setFailure(cause);
        _sendRemoteZid.setFailure(cause);
        super.channelClosed(ctx, e);
    }

    @Override
    public void connectRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        synchronized (this) {
            if (_upstreamConnected != null) throw new IllegalStateException();
            _upstreamConnected = e.getFuture();
        }
        _recvLocalZid.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future)
                    throws Exception
            {
                _sendRemoteZid.addListener(new ChannelFutureListener()
                {
                    @Override
                    public void operationComplete(ChannelFuture future)
                            throws Exception
                    {
                        finishConnecting();
                    }
                });
            }
        });
        Channels.connect(ctx, _connected, (SocketAddress)e.getValue());
    }

    private void finishConnecting()
    {
        ChannelFuture upstreamConnected;
        synchronized (this) {
            upstreamConnected = _upstreamConnected;
        }
        if (upstreamConnected == null) return;
        if (!_recvLocalZid.isSuccess()) {
            upstreamConnected.setFailure(_recvLocalZid.getCause());
        } else if (!_sendRemoteZid.isSuccess()) {
            upstreamConnected.setFailure(_sendRemoteZid.getCause());
        } else {
            upstreamConnected.setSuccess();
            Channels.fireChannelConnected(_ctx, upstreamConnected.getChannel().getRemoteAddress());
        }
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        // ignore, already added listener in channelOpen()
    }

    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception
    {
        if (e instanceof SendZephyrIDEvent) {
            int zid = ((SendZephyrIDEvent)e).getZephyrID();
            sendRemoteZid(ctx, zid);
        } else {
            super.handleDownstream(ctx, e);
        }
    }

    private void sendRemoteZid(final ChannelHandlerContext ctx, final int zid)
    {
        if (zid == ZEPHYR_INVALID_CHAN_ID) throw new IllegalArgumentException();
        synchronized (this) {
            if (_remoteZid == zid) return;
            if (_remoteZid != ZEPHYR_INVALID_CHAN_ID) throw new IllegalStateException();
            _remoteZid = zid;
        }
        _connected.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future)
                    throws Exception
            {
                if (!future.isSuccess()) return;
                Channels.write(ctx, Channels.future(ctx.getChannel()), encodeZephyrID(zid));
                _sendRemoteZid.setSuccess();
            }
        });
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception
    {
        if (e instanceof ReceiveZephyrIDEvent) {
            int zid = ((ReceiveZephyrIDEvent)e).getZephyrID();
            recvLocalZid(ctx, zid);
        }
        super.handleUpstream(ctx, e);
    }

    private void recvLocalZid(ChannelHandlerContext ctx, int zid)
    {
        if (zid == ZEPHYR_INVALID_CHAN_ID) throw new IllegalArgumentException();
        synchronized (this) {
            if (_localZid != -1) throw new IllegalStateException();
            _localZid = zid;
        }
        _recvLocalZid.setSuccess();
    }

    public static final byte[] ZEPHYR_MAGIC = ZephyrConstants.ZEPHYR_MAGIC;
    public static final int ZEPHYR_REG_PAYLOAD_LEN = ZephyrConstants.ZEPHYR_REG_PAYLOAD_LEN;
    public static final int ZEPHYR_BIND_PAYLOAD_LEN = ZephyrConstants.ZEPHYR_BIND_PAYLOAD_LEN;
    public static final int ZEPHYR_ID_LEN = ZephyrConstants.ZEPHYR_ID_LEN;
    public static int ZEPHYR_INVALID_CHAN_ID = ZephyrConstants.ZEPHYR_INVALID_CHAN_ID;

    private static ChannelBuffer encodeZephyrID(int zid)
    {
        ChannelBuffer buffer = ChannelBuffers.buffer(
                ZEPHYR_MAGIC.length + ZEPHYR_BIND_PAYLOAD_LEN + ZEPHYR_ID_LEN);
        buffer.writeBytes(ZEPHYR_MAGIC);
        buffer.writeInt(ZEPHYR_ID_LEN);
        buffer.writeInt(zid);
        return buffer;
    }

    private static int decodeZephyrID(ChannelBuffer buffer)
            throws IOException
    {
        if (buffer.readableBytes() < ZEPHYR_MAGIC.length + ZEPHYR_REG_PAYLOAD_LEN) {
            return ZEPHYR_INVALID_CHAN_ID;
        }

        buffer.markReaderIndex();
        for (int i = 0; i < ZEPHYR_MAGIC.length; ++i) {
            if (ZEPHYR_MAGIC[i] != buffer.readByte()) {
                // whoops
                buffer.resetReaderIndex();
                throw new IOException("bad magic header");
            }
        }

        if (buffer.readableBytes() < ZEPHYR_ID_LEN) {
            buffer.resetReaderIndex();
            return ZEPHYR_INVALID_CHAN_ID;
        }
        int len = buffer.readInt();

        if (len != ZEPHYR_ID_LEN) {
            throw new IOException("bad length");
        }
        if (buffer.readableBytes() < len) {
            buffer.resetReaderIndex();
            return ZEPHYR_INVALID_CHAN_ID;
        }
        int zid = buffer.readInt();
        if (zid == ZEPHYR_INVALID_CHAN_ID) {
            throw new IOException("invalid channel id");
        }
        return zid;
    }

    private static abstract class ZephyrIDEvent implements ChannelEvent
    {
        private final Channel _channel;
        private final int _zid;

        public ZephyrIDEvent(Channel channel, int zid)
        {
            _channel = channel;
            _zid = zid;
        }

        @Override
        public String toString()
        {
            // silly cast to work around IntelliJ IDEA bug
            return ((Object)this).getClass().getSimpleName() + "[" + getZephyrID() + "]";
        }

        public int getZephyrID()
        {
            return _zid;
        }

        @Override
        public Channel getChannel()
        {
            return _channel;
        }

        @Override
        public ChannelFuture getFuture()
        {
            return Channels.succeededFuture(getChannel());
        }
    }

    public static class SendZephyrIDEvent extends ZephyrIDEvent
    {
        public SendZephyrIDEvent(Channel channel, int zid)
        {
            super(channel, zid);
        }
    }

    public static class ReceiveZephyrIDEvent extends ZephyrIDEvent
    {
        public ReceiveZephyrIDEvent(Channel channel, int zid)
        {
            super(channel, zid);
        }
    }

    private static class ZephyrInitDecoder extends FrameDecoder implements
            LifeCycleAwareChannelHandler
    {
        public ZephyrInitDecoder()
        {
            super(true);
        }

        @Override
        protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer)
                throws Exception
        {
            int zid = decodeZephyrID(buffer);
            if (zid == ZEPHYR_INVALID_CHAN_ID) return null;
            l.trace("receiveZid: {}", zid);

            // remove this handler after reading header
            ctx.getPipeline().remove(this);

            ctx.sendUpstream(new ReceiveZephyrIDEvent(channel, zid));

            if (buffer.readable()) {
                // Hand off the remaining data to the next decoder
//                return buffer.readBytes(buffer.readableBytes());
                return buffer;
            } else {
                // Nothing to hand off
                return null;
            }
        }
    }
}
