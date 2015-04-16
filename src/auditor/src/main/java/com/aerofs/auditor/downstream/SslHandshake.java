/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.auditor.downstream;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.ssl.SslHandler;

public class SslHandshake extends SimpleChannelUpstreamHandler
{
    private SslHandler _ssl;

    public SslHandshake(SslHandler ssl) { _ssl = ssl; }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        _ssl.handshake();
        super.channelConnected(ctx, e);
    }
}
