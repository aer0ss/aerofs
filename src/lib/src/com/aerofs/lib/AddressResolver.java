/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

public class AddressResolver extends SimpleChannelDownstreamHandler
{
    private final Executor _executor;

    public AddressResolver(Executor executor)
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
            if (_executor == null) {
                command.run();
            } else {
                _executor.execute(command);
            }
            return;
        }
        super.connectRequested(ctx, e);
    }

    private void handleConnect(ChannelHandlerContext ctx, ChannelStateEvent event)
    {
        InetSocketAddress address = (InetSocketAddress)event.getValue();
        InetSocketAddress resolved = resolve(address);
        Channels.connect(ctx, event.getFuture(), resolved);
    }

    private static InetSocketAddress resolve(InetSocketAddress address)
    {
        if (address.isUnresolved()) {
            address = new InetSocketAddress(address.getHostName(), address.getPort());
        }
        return address;
    }
}
