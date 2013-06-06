/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.zephyr.client.pipeline;

import com.aerofs.base.Loggers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;

final class FinalUpstreamEventHandler extends SimpleChannelUpstreamHandler
{
    private static final Logger l = Loggers.getLogger(FinalUpstreamEventHandler.class);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception
    {
        l.warn("c:{} closing err:{}", e.getChannel(), e.getCause());
        ctx.getChannel().close(); // FIXME (AG): unfortunately I can't forward this reason along to the caller
    }
}
