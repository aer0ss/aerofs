/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.daemon.transport.netty.TransportMessage;
import com.aerofs.daemon.transport.netty.Unicast;
import com.aerofs.proto.Transport.PBTPHeader;
import org.jboss.netty.channel.ChannelHandlerContext;
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
class TCPProtocolHandler extends SimpleChannelUpstreamHandler
{
    private final TCP _tcp;
    private final Unicast _ucast;

    TCPProtocolHandler(TCP tcp, Unicast ucast)
    {
        _tcp = tcp;
        _ucast = ucast;
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
            reply = _tcp.processPing(false);
            break;
        case TCP_PONG:
            InetAddress remote = ((InetSocketAddress)e.getRemoteAddress()).getAddress();
            _tcp.processPong(remote, message.getDID(), message.getHeader().getTcpPong());
            break;
        case TCP_GO_OFFLINE:
            _tcp.processGoOffline(message.getDID());
            break;
        case TCP_NOP:
            break;
        default:
            // Unknown control, send upstream
            ctx.sendUpstream(e);
            return;
        }

        if (reply != null) _ucast.sendControl(message.getDID(), reply);
    }
}
