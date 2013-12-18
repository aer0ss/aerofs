/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.base.Loggers;
import com.aerofs.lib.log.LogUtil;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.nio.channels.UnresolvedAddressException;

import static org.jboss.netty.channel.Channels.close;

@org.jboss.netty.channel.ChannelHandler.Sharable
public class FinalUpstreamEventHandler extends SimpleChannelHandler
{
    private static final Logger l = Loggers.getLogger(FinalUpstreamEventHandler.class);

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        l.warn("unhandled", e.getMessage().getClass().getSimpleName());
        close(ctx.getChannel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception
    {
        l.warn("caught exc",
                LogUtil.suppress(e.getCause(),
                        UnresolvedAddressException.class, IOException.class,
                        SSLException.class, SSLHandshakeException.class));
        close(ctx.getChannel());
    }

    @Override
    public void closeRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        l.warn("close requested");
        super.closeRequested(ctx, e);
    }

    @Override
    public void disconnectRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        l.warn("disconnect requested");
        super.disconnectRequested(ctx, e);
    }
}
