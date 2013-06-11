package com.aerofs.lib;

import com.aerofs.base.Loggers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;

@org.jboss.netty.channel.ChannelHandler.Sharable
public final class BlockIncomingMessagesHandler extends SimpleChannelUpstreamHandler
{
    private static final Logger l = Loggers.getLogger(BlockIncomingMessagesHandler.class);

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        l.warn("recv unexpected message from ch rem:" + e.getChannel().getRemoteAddress());
        e.getChannel().close();
    }
}
