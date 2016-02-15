/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import com.aerofs.base.Lazy;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientBossPool;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerBossPool;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;

import java.util.concurrent.atomic.AtomicInteger;

import static com.aerofs.base.TimerUtil.getGlobalTimer;
import static java.util.concurrent.Executors.newCachedThreadPool;

public class NioChannelFactories
{
    private static final AtomicInteger THREAD_ID_COUNTER = new AtomicInteger(0);

    private static Lazy<NioServerSocketChannelFactory> _serverChannelFactory = new Lazy<>(() -> {
                NioServerBossPool bossPool = new NioServerBossPool(newCachedThreadPool(), 2,
                        (current, proposed) -> "sb" + THREAD_ID_COUNTER.getAndIncrement());

                NioWorkerPool workerPool = new NioWorkerPool(newCachedThreadPool(), 2,
                        (current, proposed) -> "sw" + THREAD_ID_COUNTER.getAndIncrement());

                return new NioServerSocketChannelFactory(bossPool, workerPool);
            });

    private static Lazy<NioClientSocketChannelFactory> _clientChannelFactory = new Lazy<>(() -> {
                NioClientBossPool bossPool = new NioClientBossPool(newCachedThreadPool(), 1,
                        getGlobalTimer(),
                        (current, proposed) -> "cb" + THREAD_ID_COUNTER.getAndIncrement());

                NioWorkerPool workerPool = new NioWorkerPool(newCachedThreadPool(), 4,
                        (current, proposed) -> "cw" + THREAD_ID_COUNTER.getAndIncrement());

                return new NioClientSocketChannelFactory(bossPool, workerPool);
            });

    public static ServerSocketChannelFactory getServerChannelFactory()
    {
        return _serverChannelFactory.get();
    }

    public static ClientSocketChannelFactory getClientChannelFactory()
    {
        return _clientChannelFactory.get();
    }
}
