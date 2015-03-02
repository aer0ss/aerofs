/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientBossPool;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerBossPool;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.Executors.newCachedThreadPool;

public final class ChannelFactories
{
    private static final AtomicInteger THREAD_ID_COUNTER = new AtomicInteger(0);

    public static ServerSocketChannelFactory newServerChannelFactory()
    {
        NioServerBossPool bossPool = new NioServerBossPool(newCachedThreadPool(), 2,
                (current, proposed) -> "sb" + THREAD_ID_COUNTER.getAndIncrement());

        NioWorkerPool workerPool = new NioWorkerPool(newCachedThreadPool(), 2,
                (current, proposed) -> "sw" + THREAD_ID_COUNTER.getAndIncrement());

        return new NioServerSocketChannelFactory(bossPool, workerPool);
    }

    public static ClientSocketChannelFactory newClientChannelFactory()
    {
        Timer timer = new HashedWheelTimer(Executors.defaultThreadFactory(),
                (current, proposed) -> "tm" + THREAD_ID_COUNTER.getAndIncrement(),
                200, TimeUnit.MILLISECONDS, 512);
        NioClientBossPool bossPool = new NioClientBossPool(newCachedThreadPool(), 1, timer,
                (current, proposed) -> "cb" + THREAD_ID_COUNTER.getAndIncrement());

        NioWorkerPool workerPool = new NioWorkerPool(newCachedThreadPool(), 2,
                (current, proposed) -> "cw" + THREAD_ID_COUNTER.getAndIncrement());

        return new NioClientSocketChannelFactory(bossPool, workerPool);
    }
}