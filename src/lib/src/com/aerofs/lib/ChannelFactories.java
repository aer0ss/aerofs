/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import com.aerofs.base.AtomicInitializer;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientBossPool;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerBossPool;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.ThreadNameDeterminer;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aerofs.base.TimerUtil.getGlobalTimer;
import static java.util.concurrent.Executors.newCachedThreadPool;

public class ChannelFactories
{
    private static final AtomicInteger THREAD_ID_COUNTER = new AtomicInteger(0);

    private static AtomicInitializer<NioServerSocketChannelFactory> _serverSocketChannelFactory =
            new AtomicInitializer<NioServerSocketChannelFactory>()
            {
                @Override
                protected @Nonnull NioServerSocketChannelFactory create()
                {
                    NioServerBossPool bossPool = new NioServerBossPool(newCachedThreadPool(), 2, new ThreadNameDeterminer()
                    {
                        @Override
                        public String determineThreadName(String current, String proposed)
                                throws Exception
                        {
                            return "sb" + THREAD_ID_COUNTER.getAndIncrement();
                        }
                    });

                    NioWorkerPool workerPool = new NioWorkerPool(newCachedThreadPool(), 2, new ThreadNameDeterminer()
                    {
                        @Override
                        public String determineThreadName(String current, String proposed)
                                throws Exception
                        {
                            return "sw" + THREAD_ID_COUNTER.getAndIncrement();
                        }
                    });

                    return new NioServerSocketChannelFactory(bossPool, workerPool);
                }
            };

    private static AtomicInitializer<NioClientSocketChannelFactory> _clientSocketChannelFactory =
            new AtomicInitializer<NioClientSocketChannelFactory>()
            {
                @Override
                protected @Nonnull NioClientSocketChannelFactory create()
                {
                    NioClientBossPool bossPool = new NioClientBossPool(newCachedThreadPool(), 1, getGlobalTimer(), new ThreadNameDeterminer()
                    {
                        @Override
                        public String determineThreadName(String current, String proposed)
                                throws Exception
                        {
                            return "cb" + THREAD_ID_COUNTER.getAndIncrement();
                        }
                    });

                    NioWorkerPool workerPool = new NioWorkerPool(newCachedThreadPool(), 2, new ThreadNameDeterminer()
                    {
                        @Override
                        public String determineThreadName(String current, String proposed)
                                throws Exception
                        {
                            return "cw" + THREAD_ID_COUNTER.getAndIncrement();
                        }
                    });

                    return new NioClientSocketChannelFactory(bossPool, workerPool);
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
