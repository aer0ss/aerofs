/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.restless;

import com.aerofs.base.net.AbstractNettyServer;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.restless.netty.JerseyHandler;
import com.aerofs.restless.providers.ContentStreamProvider;
import com.aerofs.restless.providers.GsonProvider;
import com.aerofs.restless.providers.NotFoundMapper;
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

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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

    protected final Injector _injector;

    private final Configuration _config;
    private final ResourceConfig _resources;
    private final List<String> _requestFilters = Lists.newArrayList();
    private final WebApplication _application;

    public Service(String name, InetSocketAddress addr, IPrivateKeyProvider kmgr, Injector injector)
    {
        super(name, addr, kmgr, null);

        _injector = injector;
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
        _resources.getProperties().put(
                ResourceConfig.PROPERTY_CONTAINER_REQUEST_FILTERS,
                _requestFilters.toArray(new String[_requestFilters.size()]));
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

        p.addLast("jersey", new JerseyHandler(_application, _config));
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
}
