/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.net;

import com.aerofs.lib.Loggers;
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
    static final Logger LOGGER = Loggers.getLogger(MagicHeader.class);

    public static class BadMagicHeaderException extends IOException
    {
    }

    private final byte[] _magic;
    private final int _version;
    private final int _size;

    public MagicHeader(byte[] magic, int version)
    {
        _magic = magic;
        _version = version;
        _size = _magic.length + 4;
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
//            LOGGER.debug("wrote magic header {} v{}", StringUtils.encodeHex(_magic), _version);
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
                throw new BadMagicHeaderException();
            }
//            LOGGER.debug("read magic header {} v{}", StringUtils.encodeHex(_magic), _version);

            if (buffer.readable()) {
                return buffer;
            } else {
                return null;
            }
        }

        private boolean isMatchingHeader(ChannelBuffer buffer)
        {
            for (int i = 0, len = _magic.length; i < len; ++i) {
                if (buffer.readByte() != _magic[i]) {
                    return false;
                }
            }
            int version = buffer.readInt();
            if (version != _version) {
                return false;
            }
            return true;
        }
    }
}
