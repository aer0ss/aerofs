package com.aerofs.base.net;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Base class for simple Netty servers
 */
public abstract class AbstractNettyServer
{
    protected static final Logger l = LoggerFactory.getLogger(AbstractNettyServer.class);

    protected final String _name;
    protected final SocketAddress _listenAddress;
    protected final ServerBootstrap _bootstrap;
    protected final ChannelGroup _allChannels;

    protected final ISslHandlerFactory _sslHandlerFactory;
    protected final ServerSocketChannelFactory _serverChannelFactory;

    private Channel _listenChannel;

    protected AbstractNettyServer(String name, InetSocketAddress listenAddress,
            ISslHandlerFactory sslHandlerFactory)
    {
        _name = name;
        _listenAddress = listenAddress;
        _allChannels = new DefaultChannelGroup(name);

        _sslHandlerFactory = sslHandlerFactory;
        _serverChannelFactory = getServerSocketFactory();

        _bootstrap = new ServerBootstrap(_serverChannelFactory);
    }

    public void start()
    {
        l.info("Starting {} server...", _name);
        _bootstrap.setPipelineFactory(getPipelineFactory());
        _listenChannel = _bootstrap.bind(_listenAddress);
        _allChannels.add(_listenChannel);
        l.info("Started {} server on {}", _name, getListeningPort());
    }

    public int getListeningPort()
    {
        return ((InetSocketAddress)_listenChannel.getLocalAddress()).getPort();
    }

    protected ServerSocketChannelFactory getServerSocketFactory()
    {
        return new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool());
    }

    protected ChannelPipelineFactory getPipelineFactory()
    {
        return new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                ChannelPipeline p = getSpecializedPipeline();
                if (_sslHandlerFactory != null) {
                    p.addFirst("ssl", _sslHandlerFactory.newSslHandler());
                }
                return p;
            }
        };
    }

    protected abstract ChannelPipeline getSpecializedPipeline() throws Exception;

    public void stop()
    {
        l.info("Stopping {} server...", _name);
        ChannelGroupFuture allChannelsFuture = _allChannels.close();

        // don't let the server zombify its host when channels can't be closed cleanly
        allChannelsFuture.awaitUninterruptibly(500, TimeUnit.MILLISECONDS);

        if (!allChannelsFuture.isCompleteSuccess()) {
            l.warn("unclean shutdown");
            for (ChannelFuture cf : allChannelsFuture) {
                if (!cf.isSuccess()) l.warn("{}: {}", cf.getChannel(),
                        cf.getCause());
            }
        }

        _serverChannelFactory.releaseExternalResources();
        _allChannels.clear();
    }
}
