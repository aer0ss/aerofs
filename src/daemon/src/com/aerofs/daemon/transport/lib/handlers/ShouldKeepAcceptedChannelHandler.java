/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChildChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

/**
 * {@link org.jboss.netty.channel.ChannelHandler} implementation
 * that filters accepted channels.
 * <p/>
 * Under certain circumstances accepted channels should be dropped
 * (load shedding, syncing paused, etc.) netty does not allow us to
 * avoid accepting sockets. Instead, we must allow the channel to
 * be created and then close it after the fact.
 */
public final class ShouldKeepAcceptedChannelHandler extends SimpleChannelHandler
{
    private volatile boolean enabled;

    /**
     * Allow accepted channels to live.
     * <p/>
     * This implementation <strong>closes</strong> an accepted
     * channel if {@code enabled} is set to false.
     *
     * @param enabled true if accepted channels should be allowed to stick around (i.e. we can do network I/O on them), false otherwise
     */
    public void enableAccept(boolean enabled)
    {
        this.enabled = enabled;
    }

    @Override
    public void childChannelOpen(ChannelHandlerContext ctx, ChildChannelStateEvent e)
            throws Exception
    {
        if (enabled) {
            super.childChannelOpen(ctx, e);
        } else {
            e.getChannel().close();
        }
    }
}