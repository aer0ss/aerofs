/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs;

import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import static java.util.concurrent.Executors.newCachedThreadPool;

public class ChannelFactories
{
    private static final NioServerSocketChannelFactory _serverSocketChannelFactory =
            new NioServerSocketChannelFactory(newCachedThreadPool(), newCachedThreadPool(), 2);
    private static final NioClientSocketChannelFactory _clientSocketChannelFactory =
            new NioClientSocketChannelFactory(newCachedThreadPool(), newCachedThreadPool(), 1, 2);

    public static void init_()
    {
        // noop - ensure that static initializers are run
    }

    public static ServerSocketChannelFactory getServerChannelFactory()
    {
        return _serverSocketChannelFactory;
    }

    public static ClientSocketChannelFactory getClientChannelFactory()
    {
        return _clientSocketChannelFactory;
    }
}
