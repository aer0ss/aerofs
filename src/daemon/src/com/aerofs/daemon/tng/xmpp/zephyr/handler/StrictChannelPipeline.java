/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr.handler;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;

public class StrictChannelPipeline extends DefaultChannelPipeline
{
    @Override
    protected void notifyHandlerException(ChannelEvent e, Throwable t)
    {
        if (e instanceof ExceptionEvent) {
            throw new AssertionError(t);
        }

        super.notifyHandlerException(e, t);
    }
}
