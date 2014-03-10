/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.lib.IChannelData;
import com.aerofs.daemon.transport.lib.IIncomingChannelListener;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;

import static com.aerofs.daemon.transport.lib.ChannelDataUtil.getChannelData;
import static com.google.common.base.Preconditions.checkState;

@Sharable
public final class IncomingChannelHandler extends SimpleChannelHandler
{
    private static final Logger l = Loggers.getLogger(IncomingChannelHandler.class);

    private final IIncomingChannelListener incomingChannelListener;

    public IncomingChannelHandler(IIncomingChannelListener incomingChannelListener)
    {
        this.incomingChannelListener = incomingChannelListener;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        checkState(e.getChannel().getAttachment() != null);

        IChannelData channelData = getChannelData(e);
        DID did = channelData.getRemoteDID();
        Channel channel = e.getChannel();

        l.info("{} incoming connection on {}", did, channel);
        incomingChannelListener.onIncomingChannel(did, channel);

        super.channelConnected(ctx, e);
    }
}
