/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.restless;

import com.aerofs.base.net.AbstractNettyServer;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.restless.jersey.VersionFilterFactory;
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
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpServerCodec;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

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
public class Service extends AbstractNettyServer
{
    static {
        // Jersey uses JUL, we use slf4j hence the need for bridging...
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

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

    private final Executor _executor;

    private final Configuration _config;
    private final ResourceConfig _resources;
    private final List<String> _requestFilters = Lists.newArrayList();
    private final WebApplication _application;

    public Service(String name, InetSocketAddress addr, IPrivateKeyProvider kmgr, Injector injector)
    {
        this(name, addr, kmgr, injector, null);
    }

    public Service(String name, InetSocketAddress addr, IPrivateKeyProvider kmgr, Injector injector,
            @Nullable Executor executor)
    {
        super(name, addr, kmgr, null);

        _injector = injector;
        _executor = executor;
        _config = injector.getInstance(Configuration.class);
        _application = WebApplicationFactory.createWebApplication();
        _resources = getResourceConfiguration();
    }

    @Override
    public void start()
    {
        if (!_application.isInitiated()) {
            _application.initiate(_resources, new GuiceComponentProviderFactory(_resources, _injector));
        }
        super.start();
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

    @Override
    public ChannelPipeline getSpecializedPipeline()
    {
        ChannelPipeline p = Channels.pipeline(
                new HttpServerCodec(),
                new ChunkedWriteHandler());

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
}
