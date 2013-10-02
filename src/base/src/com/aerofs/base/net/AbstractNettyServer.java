package com.aerofs.base.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Base class for simple Netty servers
 */
public abstract class AbstractNettyServer
{
    protected static final Logger l = Loggers.getLogger(AbstractNettyServer.class);

    protected final String _name;
    protected final SocketAddress _localSocket;
    protected final ServerBootstrap _bootstrap;
    protected final ChannelGroup _allChannels;

    protected final SSLEngineFactory _serverSslEngineFactory;
    protected final ServerSocketChannelFactory _serverChannelFactory;

    protected AbstractNettyServer(String name, int port,
            @Nullable IPrivateKeyProvider key, @Nullable ICertificateProvider cacert)
    {
        this(name, new InetSocketAddress(port),
                new SSLEngineFactory(Mode.Server, Platform.Desktop, key, cacert, null));
    }

    protected AbstractNettyServer(String name, InetSocketAddress localSocket,
            SSLEngineFactory serverSslEngineFactory)
    {
        _name = name;
        _localSocket = localSocket;
        _allChannels = new DefaultChannelGroup(name);
        _serverSslEngineFactory = serverSslEngineFactory;

        _serverChannelFactory = new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool());

        _bootstrap = new ServerBootstrap(_serverChannelFactory);
    }

    public int start()
    {
        l.info("Starting {} server...", _name);
        _bootstrap.setPipelineFactory(pipelineFactory());
        final Channel serverChannel = _bootstrap.bind(_localSocket);
        _allChannels.add(serverChannel);
        return ((InetSocketAddress)serverChannel.getLocalAddress()).getPort();
    }

    protected abstract ChannelPipelineFactory pipelineFactory();

    public void stop()
    {
        l.info("Stopping {} server...", _name);
        ChannelGroupFuture future = _allChannels.close();

        // don't let the server zombify its host when channels can't be closed cleanly
        future.awaitUninterruptibly(500, TimeUnit.MILLISECONDS);

        if (!future.isCompleteSuccess()) {
            l.warn("unclean shutdown");
            for (ChannelFuture cf : future) {
                if (!cf.isSuccess()) l.warn("{}: {}", cf.getChannel(), cf.getCause());
            }
        }

        _serverChannelFactory.releaseExternalResources();
        _allChannels.clear();
    }
}
