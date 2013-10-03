/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import com.aerofs.base.AtomicInitializer;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;

import javax.annotation.Nonnull;

import static com.aerofs.base.TimerUtil.getGlobalTimer;
import static java.util.concurrent.Executors.newCachedThreadPool;

public class ChannelFactories
{
    private static AtomicInitializer<NioServerSocketChannelFactory> _serverSocketChannelFactory =
            new AtomicInitializer<NioServerSocketChannelFactory>() {
                @Override
                protected @Nonnull NioServerSocketChannelFactory create()
                {
                    return new NioServerSocketChannelFactory(
                            newCachedThreadPool(), newCachedThreadPool(), 2);
                }
            };

    private static AtomicInitializer<NioClientSocketChannelFactory> _clientSocketChannelFactory =
            new AtomicInitializer<NioClientSocketChannelFactory>() {
                @Override
                protected @Nonnull NioClientSocketChannelFactory create()
                {
                    return new NioClientSocketChannelFactory(
                            newCachedThreadPool(), 1,
                            new NioWorkerPool(newCachedThreadPool(), 2),
                            getGlobalTimer());
                }
            };

    public static ServerSocketChannelFactory getServerChannelFactory()
    {
        return _serverSocketChannelFactory.get();
    }

    public static ClientSocketChannelFactory getClientChannelFactory()
    {
        return _clientSocketChannelFactory.get();
    }
}
