/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.nativesocket;

import com.aerofs.base.BaseLogUtil;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractNativeSocketPeerAuthenticator extends SimpleChannelUpstreamHandler
{
    private static final Logger l = LoggerFactory.getLogger(AbstractNativeSocketPeerAuthenticator.class);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        // Close the connection when an exception is raised.
        l.warn("Unexpected exception: ", BaseLogUtil.suppress(e.getCause()));
        e.getChannel().close();
    }
}