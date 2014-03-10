/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.net;

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
import java.util.Arrays;

import static com.aerofs.base.BaseUtil.hexEncode;

public class MagicHeader
{
    private static final Logger l = Loggers.getLogger(MagicHeader.class);

    public static class WriteMagicHeaderHandler extends SimpleChannelHandler
    {
        private final byte[] _magic;
        private boolean _done = false;

        public WriteMagicHeaderHandler(byte[] magic)
        {
            _magic = magic;
        }

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
            ChannelBuffer buffer = ChannelBuffers.buffer(_magic.length);
            buffer.writeBytes(_magic);
            Channels.write(ctx, Channels.future(ctx.getChannel()), buffer);
            ctx.getPipeline().remove(this);

            l.trace("wrote magic header 0x{}", hexEncode(_magic));
        }
    }

    public static class ReadMagicHeaderHandler extends FrameDecoder
    {
        private final byte[] _magic;

        public ReadMagicHeaderHandler(byte[] magic)
        {
            _magic = magic;
        }

        @Override
        protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer)
                throws Exception
        {
            if (buffer.readableBytes() < _magic.length) return null;
            final int readerIndex = buffer.readerIndex();
            ctx.getPipeline().remove(this);

            byte[] magic = new byte[_magic.length];
            buffer.readBytes(magic);

            if (!Arrays.equals(_magic, magic)) {
                buffer.readerIndex(readerIndex);
                throw new ExBadMagicHeader(_magic, magic);
            }

            l.trace("read magic header 0x{}", hexEncode(_magic));

            return (buffer.readable()) ? buffer : null;
        }
    }

    public static class ExBadMagicHeader extends IOException
    {
        private static final long serialVersionUID = 1;

        ExBadMagicHeader(byte[] expected, byte[] actual)
        {
            super("magic mismatch. exp:" + hexEncode(expected) + " act:" + hexEncode(actual));
        }
    }
}
