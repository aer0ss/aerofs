/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.lib.Unicast;
import com.aerofs.daemon.transport.lib.handlers.TransportMessage;
import com.aerofs.proto.Transport.PBTPHeader;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Handlers TCP-specific control messages. Unknown control messages are passed to the next upstream
 * handler.
 *
 * This handler is sharable accross all connections for a given TCP instance
 */
final class TCPProtocolHandler extends SimpleChannelUpstreamHandler
{
    private final Stores stores;
    private final Unicast unicast;

    TCPProtocolHandler(Stores stores, Unicast unicast)
    {
        this.stores = stores;
        this.unicast = unicast;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        // this message should be the first one sent out
        // it indicates which stores this device is part of
        PBTPHeader pong = stores.newPongMessage(false);
        if (pong != null) {
            e.getChannel().write(TPUtil.newControl(pong));
        }

        super.channelOpen(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (!(e.getMessage() instanceof TransportMessage)) return;
        final TransportMessage message = (TransportMessage) e.getMessage();

        PBTPHeader reply = null;

        switch (message.getHeader().getType()) {
        case TCP_PING:
            reply = stores.processPing(false);
            break;
        case TCP_PONG:
            InetAddress remote = ((InetSocketAddress)e.getRemoteAddress()).getAddress();
            stores.processPong(remote, message.getDID(), message.getHeader().getTcpPong());
            break;
        case TCP_GO_OFFLINE:
            stores.processGoOffline(message.getDID());
            break;
        case TCP_NOP:
            break;
        default: // unknown control message, pass it on
            ctx.sendUpstream(e);
            return;
        }

        if (reply != null) {
            unicast.sendControl(message.getDID(), reply);
        }
    }
}
