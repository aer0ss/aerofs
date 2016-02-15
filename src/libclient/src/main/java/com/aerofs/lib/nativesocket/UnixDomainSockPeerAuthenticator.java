/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.nativesocket;

import com.aerofs.lib.os.IOSUtil;
import com.flipkart.phantom.netty.common.OioAcceptedSocketChannel;
import com.google.inject.Inject;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketException;

public class UnixDomainSockPeerAuthenticator extends AbstractNativeSocketPeerAuthenticator
{
    private static final Logger l = LoggerFactory.getLogger(UnixDomainSockPeerAuthenticator.class);
    private final IOSUtil _iosutil;

    @Inject
    public UnixDomainSockPeerAuthenticator(IOSUtil iosUtil)
    {
        this._iosutil = iosUtil;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws IOException
    {
        OioAcceptedSocketChannel channel = (OioAcceptedSocketChannel) ctx.getChannel();
        if (_iosutil.getUserUid() != channel.getPeerUID()) {
            throw new NativeSocketUnknownPeerException("Unknown peer tried to connect to Server.");
        }
        l.info("Peer UID = Server UID. Verified.");
        ctx.sendUpstream(e);
    }
}