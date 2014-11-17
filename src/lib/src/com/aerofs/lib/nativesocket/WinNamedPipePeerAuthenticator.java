/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.nativesocket;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;

public class WinNamedPipePeerAuthenticator extends AbstractNativeSocketPeerAuthenticator
{
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
    {
        // For Windows we specify a security attribute while creating a named pipe. We don't
        // need to authenticate here explicitly because the client wouldn't get a handle in the
        // first place if it didn't have the required access token. So, the fact that then channel
        // connects means the client is authenticated.
        ctx.sendUpstream(e);
    }
}
