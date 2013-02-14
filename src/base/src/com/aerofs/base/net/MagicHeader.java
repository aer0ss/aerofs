/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.net;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.SocketAddress;

public class MagicHeader
{
    public static class ExBadMagicHeader extends IOException
    {
        private static final long serialVersionUID = 1;
    }

    private static final Logger l = Loggers.getLogger(MagicHeader.class);
    private final byte[] _magic;
    private final int _version;
    private final int _size;

    public MagicHeader(byte[] magic, int version)
    {
        _magic = magic;
        _version = version;
        _size = _magic.length + Integer.SIZE / Byte.SIZE; // add 4 bytes for the version number
    }

    public class WriteMagicHeaderHandler extends SimpleChannelHandler
    {
        boolean _done = false;

        @Override
        public void connectRequested(final ChannelHandlerContext ctx, final ChannelStateEvent event)
                throws Exception
        {
            ChannelFuture future = Channels.future(event.getChannel());
            future.addListener(new ChannelFutureListener()
            {
                @Override
                public void operationComplete(ChannelFuture future)
                        throws Exception
                {
                    if (future.isSuccess()) {
                        try {
                            writeHeader(ctx);
                        } catch (Exception e) {
                            event.getFuture().setFailure(e);
                            throw e;
                        }
                        event.getFuture().setSuccess();
                    } else if (!future.isCancelled() || !event.getFuture().cancel()) {
                        event.getFuture().setFailure(future.getCause());
                    }
                }
            });
            Channels.connect(ctx, future, (SocketAddress)event.getValue());
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception
        {
            writeHeader(ctx);
            super.channelConnected(ctx, e);
        }

        private synchronized void writeHeader(ChannelHandlerContext ctx)
        {
            if (_done) return;
            _done = true;
            ChannelBuffer buffer = ChannelBuffers.buffer(_size);
            buffer.writeBytes(_magic);
            buffer.writeInt(_version);
            Channels.write(ctx, Channels.future(ctx.getChannel()), buffer);
            ctx.getPipeline().remove(this);

            l.debug("wrote magic header {} v{}", BaseUtil.hexEncode(_magic), _version);
        }
    }

    public class ReadMagicHeaderHandler extends FrameDecoder
    {
        @Override
        protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer)
                throws Exception
        {
            if (buffer.readableBytes() < _size) return null;
            final int readerIndex = buffer.readerIndex();
            ctx.getPipeline().remove(this);

            if (!isMatchingHeader(buffer)) {
                buffer.readerIndex(readerIndex);
                throw new ExBadMagicHeader();
            }

            l.debug("read magic header {} v{}", BaseUtil.hexEncode(_magic), _version);

            return (buffer.readable()) ? buffer : null;
        }

        private boolean isMatchingHeader(ChannelBuffer buffer)
        {
            for (byte b : _magic) {
                if (buffer.readByte() != b) return false;
            }
            int version = buffer.readInt();
            return (version == _version);
        }
    }
}
