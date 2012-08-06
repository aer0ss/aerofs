package com.aerofs.daemon.transport.xmpp.zephyr.client.netty.handler;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelHandler;

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
public class ConnectionProxyHandler extends SimpleChannelHandler {

    private final SocketAddress _proxyAddress;
    private InetSocketAddress _destinationAddress;

    public ConnectionProxyHandler(SocketAddress proxyAddress)
    {
        assert proxyAddress != null;

        _proxyAddress = proxyAddress;
        _destinationAddress = null;
    }

    @Override
    public void connectRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        _destinationAddress = (InetSocketAddress) e.getValue();
        Channels.connect(ctx, e.getFuture(), _proxyAddress);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        assert _destinationAddress != null;

        // This handler is done, so remove it
        ctx.getPipeline().remove(this);

        // Notify the upstream clients that we connected successfully to the
        // original destination address, even though we actually connected
        // to the proxy
        Channels.fireChannelConnected(ctx, _destinationAddress);
    }

}
