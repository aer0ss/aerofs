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
import org.jboss.netty.util.ThreadNameDeterminer;

import java.util.concurrent.atomic.AtomicInteger;

import static com.aerofs.base.TimerUtil.getGlobalTimer;
import static java.util.concurrent.Executors.newCachedThreadPool;

public final class ChannelFactories
{
    private static final AtomicInteger THREAD_ID_COUNTER = new AtomicInteger(0);

    public static ServerSocketChannelFactory newServerChannelFactory()
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

    public static ClientSocketChannelFactory newClientChannelFactory()
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
}