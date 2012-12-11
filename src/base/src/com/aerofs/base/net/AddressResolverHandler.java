/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.net;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

public class AddressResolverHandler extends SimpleChannelDownstreamHandler
{
    private final Executor _executor;

    public AddressResolverHandler(Executor executor)
    {
        _executor = executor;
    }

    @Override
    public void connectRequested(final ChannelHandlerContext ctx, final ChannelStateEvent e)
            throws Exception
    {
        InetSocketAddress address = (InetSocketAddress)e.getValue();
        if (address.isUnresolved()) {
            Runnable command = new Runnable()
            {
                @Override
                public void run()
                {
                    handleConnect(ctx, e);
                }
            };
            if (_executor != null) {
                _executor.execute(command);
            } else {
                command.run();
            }
            return;
        } else {
            super.connectRequested(ctx, e);
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, ChannelStateEvent event)
    {
        try {
            InetSocketAddress address = (InetSocketAddress)event.getValue();
            InetSocketAddress resolved = resolve(address);
            Channels.connect(ctx, event.getFuture(), resolved);
        } catch (Exception e) {
            event.getFuture().setFailure(e);
            Channels.fireExceptionCaught(ctx, e);
        }
    }

    private static InetSocketAddress resolve(InetSocketAddress address)
    {
        if (address.isUnresolved()) {
            address = new InetSocketAddress(address.getHostName(), address.getPort());
        }
        return address;
    }
}
