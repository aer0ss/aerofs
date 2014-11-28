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

public abstract class CoreProtocolHandlers
{
    private static final Logger l = Loggers.getLogger(CoreProtocolHandlers.class);

    private CoreProtocolHandlers() { } // private to prevent instantiation

    public static final class SendCoreProtocolVersionHandler extends SimpleChannelHandler
    {
        private final byte[] protocolVersion;
        private boolean versionHeaderSent = false; // protected by 'this'

        public SendCoreProtocolVersionHandler(byte[] protocolVersion)
        {
            this.protocolVersion = protocolVersion;
        }

        // TODO (AG): I believe that I can simply store the future and write the header in channelConnected

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
            if (versionHeaderSent) return;
            versionHeaderSent = true;

            ChannelBuffer buffer = ChannelBuffers.buffer(protocolVersion.length);
            buffer.writeBytes(protocolVersion);
            Channels.write(ctx, Channels.future(ctx.getChannel()), buffer);
            ctx.getPipeline().remove(this);

            l.trace("wrote magic header 0x{}", hexEncode(protocolVersion));
        }
    }

    public static final class RecvCoreProtocolVersionHandler extends FrameDecoder
    {
        private final byte[] protocolVersion;

        public RecvCoreProtocolVersionHandler(byte[] protocolVersion)
        {
            this.protocolVersion = protocolVersion;
        }

        @Override
        protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer)
                throws Exception
        {
            if (buffer.readableBytes() < protocolVersion.length) return null;
            final int readerIndex = buffer.readerIndex();
            ctx.getPipeline().remove(this);

            byte[] magic = new byte[protocolVersion.length];
            buffer.readBytes(magic);

            if (!Arrays.equals(protocolVersion, magic)) {
                buffer.readerIndex(readerIndex);
                throw new ExBadMagicHeader(protocolVersion, magic);
            }

            l.trace("read magic header 0x{}", hexEncode(protocolVersion));

            return (buffer.readable()) ? buffer : null;
        }
    }

    public static final class ExBadMagicHeader extends IOException
    {
        private static final long serialVersionUID = 1;

        ExBadMagicHeader(byte[] expected, byte[] actual)
        {
            super("magic mismatch. exp:" + hexEncode(expected) + " act:" + hexEncode(actual));
        }
    }
}
