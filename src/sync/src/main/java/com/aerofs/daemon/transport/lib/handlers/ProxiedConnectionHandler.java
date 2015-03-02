package com.aerofs.daemon.transport.lib.handlers;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.jboss.netty.channel.Channels.connect;
import static org.jboss.netty.channel.Channels.fireChannelConnected;

/**
 * Intercepts a connectRequested event and replaces the destination address
 * with the address to the proxy.
 * <p>
 * Upon successful connection, this handler
 * intercepts the channelConnected event and replaces the address connected to
 * (which is the proxy address at this point) with the original destination
 * address. This makes it look like the connection was never intercepted
 *
 */
public final class ProxiedConnectionHandler extends SimpleChannelHandler
{
    private final SocketAddress proxyAddress;
    private InetSocketAddress destinationAddress;

    public ProxiedConnectionHandler(SocketAddress proxyAddress)
    {
        this.proxyAddress = proxyAddress;
        this.destinationAddress = null;
    }

    @Override
    public void connectRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        destinationAddress = (InetSocketAddress) e.getValue();
        connect(ctx, e.getFuture(), proxyAddress);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        checkNotNull(destinationAddress);

        // remove ourselves, because the connection succeeded, and then
        // notify the upstream clients that we connected successfully to the
        // original destination address, even though we actually connected
        // to the proxy

        ctx.getPipeline().remove(this);
        fireChannelConnected(ctx, destinationAddress);
    }
}
