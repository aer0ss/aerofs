/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.daemon.rest;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.rest.providers.GsonProvider;
import com.aerofs.daemon.rest.netty.JerseyHandler;
import com.aerofs.daemon.rest.netty.NettyServer;
import com.aerofs.daemon.rest.resources.FilesResource;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.core.spi.component.ioc.IoCComponentProviderFactory;
import com.sun.jersey.guice.spi.container.GuiceComponentProviderFactory;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.container.WebApplicationFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

public class RestService
{
    static {
        // Jersey uses JUL, we use slf4j hence the need for bridging...
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    private static final Logger l = Loggers.getLogger(RestService.class);

    // Base URI for the service. Must end with a slash.
    public static final URI BASE_URI = URI.create("http://localhost:8080/");

    // Array of package names that Jersey will scan for annotated classes
    private static final String[] RESOURCES_PACKAGES = {
            FilesResource.class.getPackage().getName(),
            GsonProvider.class.getPackage().getName()
    };

    private final Injector _injector;
    private NettyServer _server;

    @Inject
    RestService(Injector injector)
    {
        _injector = injector;
    }

    public RestService start()
    {
        checkState(BASE_URI.toString().endsWith("/"));
        startServer(getResourceConfiguration(), BASE_URI);
        return this;
    }

    public void addShutdownHook()
    {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run()
            {
                _server.stopServer();
            }
        });
    }

    public void stop()
    {
        _server.stopServer();

    }

    private void startServer(final ResourceConfig resourceConfig, final URI baseUri)
    {
        WebApplication wa = WebApplicationFactory.createWebApplication();
        IoCComponentProviderFactory ioc = new GuiceComponentProviderFactory(resourceConfig, _injector);

        final JerseyHandler jerseyHandler = new JerseyHandler(wa);
        if (!wa.isInitiated()) wa.initiate(resourceConfig, ioc);

        InetSocketAddress localSocket = new InetSocketAddress(baseUri.getHost(), baseUri.getPort());
        ChannelPipelineFactory pipelineFactory = new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                return Channels.pipeline(
                        new HttpServerCodec(),
                        jerseyHandler);
            }
        };

        _server = new NettyServer(pipelineFactory, localSocket);
        _server.startServer();
    }

    private ResourceConfig getResourceConfiguration()
    {
        final Map<String, Object> props = new HashMap<String, Object>();
        props.put(PackagesResourceConfig.PROPERTY_PACKAGES, RESOURCES_PACKAGES);
        ResourceConfig cfg = new PackagesResourceConfig(props);
        cfg.getFeatures().put(ResourceConfig.FEATURE_DISABLE_WADL, Boolean.TRUE);
        return cfg;
    }
}
