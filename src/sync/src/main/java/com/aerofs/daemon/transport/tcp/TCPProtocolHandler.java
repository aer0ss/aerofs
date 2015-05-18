/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.daemon.transport.lib.TransportProtocolUtil;
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
    private final TCPStores stores;
    private final Unicast unicast;

    TCPProtocolHandler(TCPStores stores, Unicast unicast)
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
            e.getChannel().write(TransportProtocolUtil.newControl(pong));
        }

        super.channelOpen(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (!(e.getMessage() instanceof TransportMessage)) return;
        final TransportMessage message = (TransportMessage) e.getMessage();

        switch (message.getHeader().getType()) {
        case TCP_PING:
            PBTPHeader reply = stores.processPing(false);
            if (reply != null) unicast.sendControl(message.getDID(), reply);
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
        }
    }
}
