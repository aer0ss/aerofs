/*
 * Copyright (c) Air Computing Inc., 2013.
 */
package com.aerofs.daemon.rest.netty;

import com.aerofs.base.Loggers;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;

public final class NettyServer
{
    private static final Logger l = Loggers.getLogger(NettyServer.class);

    private final ServerBootstrap _bootstrap;
    private final SocketAddress _localSocket;
    private final ChannelGroup _allChannels = new DefaultChannelGroup("jersey_netty_server");

    public NettyServer(final ChannelPipelineFactory pipelineFactory, final SocketAddress localSocket)
    {
        _localSocket = localSocket;

        final ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

        bootstrap.setPipelineFactory(pipelineFactory);
        _bootstrap = bootstrap;
    }

    public int startServer()
    {
        l.info("Starting server....");
        final Channel serverChannel = _bootstrap.bind(_localSocket);
        _allChannels.add(serverChannel);
        return ((InetSocketAddress)serverChannel.getLocalAddress()).getPort();
    }

    public void stopServer()
    {
        l.info("Stopping server....");
        _allChannels.close().awaitUninterruptibly();
        _bootstrap.getFactory().releaseExternalResources();
        _allChannels.clear();
    }
}
