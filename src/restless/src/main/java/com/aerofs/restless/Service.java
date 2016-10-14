/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.restless;

import com.aerofs.restless.jersey.VersionFilterFactory;
import com.aerofs.restless.netty.FairChunkedWriteHandler;
import com.aerofs.restless.netty.JerseyHandler;
import com.aerofs.restless.providers.ContentStreamProvider;
import com.aerofs.restless.providers.GsonProvider;
import com.aerofs.restless.providers.NotFoundMapper;
import com.aerofs.restless.providers.VersionProvider;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Injector;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.guice.spi.container.GuiceComponentProviderFactory;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.container.WebApplicationFactory;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Base class for Restless service
 *
 * Restless is small library that aims to make it easy to build RESTful services and is designed
 * to be easy to integrate into existing codebases (something that is pretty much impossible with
 * Dropwizard).
 *
 * It is basically a layer of glue between Netty, Jersey and Gson with some helpers for versioning,
 * authentication and content streaming (both input and output).
 */
public class Service
{
    static {
        org.jboss.netty.logging.InternalLoggerFactory.setDefaultFactory(new org.jboss.netty.logging.Slf4JLoggerFactory());

        // Jersey uses JUL, we use slf4j hence the need for bridging...
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    protected static final Logger l = LoggerFactory.getLogger(Service.class);

    private final static Set<Class<?>> PROVIDERS = ImmutableSet.of(
            GsonProvider.class,
            ContentStreamProvider.class,
            NotFoundMapper.class
    );

    // accept any version in the URL and on a per-request basis
    public static final String VERSION = "/v{version: [0-9]+\\.[0-9]+}";

    public static final String DUMMY_LOCATION = "https://dummy/";
    public static final URI DUMMY_BASE_URI = URI.create(DUMMY_LOCATION);

    public static final String JERSEY_HANLDER = "jersey";

    protected final Injector _injector;
    protected final String _name;
    protected final SocketAddress _listenAddress;
    protected final ServerBootstrap _bootstrap;
    protected final ChannelGroup _allChannels;
    protected final ServerSocketChannelFactory _serverChannelFactory;

    protected final FairChunkedWriteHandler _chunkedWriteHandler;

    private final Executor _executor;

    private final Configuration _config;
    private final ResourceConfig _resources;
    private final List<String> _requestFilters = Lists.newArrayList();
    private final WebApplication _application;
    private Channel _listenChannel;

    public Service(String name, InetSocketAddress addr, Injector injector)
    {
        this(name, addr, injector, null);
    }

    public Service(String name, InetSocketAddress addr,
            Injector injector, @Nullable Executor executor)
    {
        _name = name;
        _listenAddress = addr;
        _allChannels = new DefaultChannelGroup(name);
        _serverChannelFactory = getServerSocketFactory();
        _bootstrap = new ServerBootstrap(this._serverChannelFactory);
        _chunkedWriteHandler = new FairChunkedWriteHandler();
        _injector = injector;
        _executor = executor;
        _config = injector.getInstance(Configuration.class);
        _application = WebApplicationFactory.createWebApplication();
        _resources = getResourceConfiguration();
    }

    public void start()
    {
        if (!_application.isInitiated()) {
            _application.initiate(_resources, new GuiceComponentProviderFactory(_resources, _injector));
        }
        l.info("Starting {} server...", _name);
        _bootstrap.setPipelineFactory(getPipelineFactory());
        _listenChannel = _bootstrap.bind(_listenAddress);
        _allChannels.add(_listenChannel);
        l.info("Started {} server on {}", _name, getListeningPort());
    }

    public void stop()
    {
        l.info("Stopping {} server...", _name);
        ChannelGroupFuture allChannelsFuture = _allChannels.close();

        // don't let the server zombify its host when channels can't be closed cleanly
        allChannelsFuture.awaitUninterruptibly(500, TimeUnit.MILLISECONDS);

        if (!allChannelsFuture.isCompleteSuccess()) {
            l.warn("unclean shutdown");
            for (ChannelFuture cf : allChannelsFuture) {
                if (!cf.isSuccess()) l.warn("{}: {}", cf.getChannel(), cf.getCause());
            }
        }

        _serverChannelFactory.releaseExternalResources();
        _allChannels.clear();
        _chunkedWriteHandler.stop();

        if (_executor != null && _executor instanceof ExecutorService) {
            ((ExecutorService)_executor).shutdown();
        }
    }

    public int getListeningPort()
    {
        return ((InetSocketAddress)_listenChannel.getLocalAddress()).getPort();
    }

    protected void addRequestFilter(Class<? extends ContainerRequestFilter> clazz)
    {
        Preconditions.checkNotNull(clazz);
        _requestFilters.add(clazz.getCanonicalName());
        _resources.getProperties().put(ResourceConfig.PROPERTY_CONTAINER_REQUEST_FILTERS,
                _requestFilters.toArray(new String[_requestFilters.size()]));
    }

    protected void addResource(Class<?> clazz)
    {
        Preconditions.checkNotNull(clazz);
        _resources.getClasses().add(clazz);
    }

    protected void addResource(Object o)
    {
        Preconditions.checkNotNull(o);
        _resources.getSingletons().add(o);
    }

    public ChannelPipeline getSpecializedPipeline()
    {
        ChannelPipeline p = Channels.pipeline(
                new HttpServerCodec(),
                _chunkedWriteHandler);

        // name the last handler to allow subclasses to insert handlers before it
        // e.g. an ExecutionHandler or some transaction logic
        p.addLast(JERSEY_HANLDER, new JerseyHandler(_application, _executor, _config));
        return p;
    }

    protected ResourceConfig getResourceConfiguration()
    {
        ResourceConfig cfg = new DefaultResourceConfig(Sets.union(PROVIDERS, singletons()));
        cfg.getFeatures().put(ResourceConfig.FEATURE_DISABLE_WADL, Boolean.TRUE);
        return cfg;
    }

    protected Set<Class<?>> singletons()
    {
        return Collections.emptySet();
    }

    protected void enableVersioning()
    {
        _resources.getProperties()
                .put(ResourceConfig.PROPERTY_RESOURCE_FILTER_FACTORIES, VersionFilterFactory.class);
        _resources.getClasses().add(VersionProvider.class);
    }

    protected ServerSocketChannelFactory getServerSocketFactory()
    {
        return new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool());
    }

    protected ChannelPipelineFactory getPipelineFactory()
    {
        return Service.this::getSpecializedPipeline;
    }
}
