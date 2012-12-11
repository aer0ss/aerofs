/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.net;

import com.aerofs.base.Loggers;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.UpstreamMessageEvent;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.slf4j.Logger;

public class TraceHandler implements ChannelUpstreamHandler, ChannelDownstreamHandler
{
    private static final Logger l = Loggers.getLogger(TraceHandler.class);

    Logger _logger = l;

    private long _sending;
    private long _sent;
    private long _received;

    public void log(ChannelHandlerContext ctx, ChannelEvent e)
    {
        if (_logger.isInfoEnabled()) {
            String prefix = ctx.getName() + ": ";
            String msg = e.toString();
            if (e instanceof ExceptionEvent) {
                _logger.debug(prefix + msg, ((ExceptionEvent)e).getCause());
            } else {
                if (e instanceof MessageEvent) {
                    MessageEvent event = (MessageEvent)e;
                    if (event.getMessage() instanceof ChannelBuffer) {
                        ChannelBuffer buffer = (ChannelBuffer)event.getMessage();
                        if (event instanceof UpstreamMessageEvent) {
                            _sending += buffer.readableBytes();
                            msg += ", total " + _sending;
                        } else if (event instanceof DownstreamMessageEvent) {
                            _received += buffer.readableBytes();
                            msg += ", total " + _received;
                        }
                    }
                } else if (e instanceof WriteCompletionEvent) {
                    WriteCompletionEvent event = (WriteCompletionEvent)e;
                    _sent += event.getWrittenAmount();
                    msg += ", total " + _sent;
                } else if (e instanceof ChannelStateEvent) {
                    ChannelStateEvent event = (ChannelStateEvent)e;
                    msg = event.getChannel() + " " + event.getState() + ": " + event.getValue();
                }
                _logger.debug(prefix + msg);
            }
        }
    }

    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception
    {
        log(ctx, e);
        ctx.sendDownstream(e);
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception
    {
        log(ctx, e);
        ctx.sendUpstream(e);
    }
}
