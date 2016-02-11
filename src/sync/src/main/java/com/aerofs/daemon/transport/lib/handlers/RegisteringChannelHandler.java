/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.daemon.transport.lib.ChannelData;
import com.aerofs.daemon.transport.lib.ChannelRegisterer;
import com.aerofs.daemon.transport.lib.TransportUtil;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;

import static com.aerofs.daemon.transport.lib.TransportUtil.getChannelData;

@Sharable
public final class RegisteringChannelHandler extends SimpleChannelHandler
{
    private static final Logger l = Loggers.getLogger(RegisteringChannelHandler.class);

    private final ChannelRegisterer reg;

    public RegisteringChannelHandler(ChannelRegisterer reg)
    {
        this.reg = reg;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        ChannelData channelData = getChannelData(e.getChannel());
        DID did = channelData.getRemoteDID();
        Channel channel = e.getChannel();

        if (!reg.registerChannel(channel, did)) {
            l.info("{} reject incoming connection on {}", did, TransportUtil.hexify(channel));
            channel.close();
        } else {
            l.info("{} accept incoming connection on {}", did, TransportUtil.hexify(channel));
            ctx.sendUpstream(e);
        }
    }
}
