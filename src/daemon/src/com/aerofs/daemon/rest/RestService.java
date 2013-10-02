/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.daemon.rest;

import com.aerofs.base.net.AbstractNettyServer;
import com.aerofs.daemon.rest.providers.GsonProvider;
import com.aerofs.daemon.rest.netty.JerseyHandler;
import com.aerofs.daemon.rest.resources.FilesResource;
import com.aerofs.daemon.transport.netty.BootstrapFactoryUtil;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.guice.spi.container.GuiceComponentProviderFactory;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.container.WebApplicationFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpServerCodec;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.Map;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.google.common.base.Preconditions.checkNotNull;

public class RestService extends AbstractNettyServer
{
    static {
        // Jersey uses JUL, we use slf4j hence the need for bridging...
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    public static final String VERSION = "/v0.8";

    // Port for the service. 0 to use any available port (default)
    // configurable for firewall-friendliness
    private static final int PORT = getIntegerProperty("rest.port", 8080);

    // Array of package names that Jersey will scan for annotated classes
    private static final String[] RESOURCES_PACKAGES = {
            FilesResource.class.getPackage().getName(),
            GsonProvider.class.getPackage().getName()
    };

    private final Injector _injector;
    private final WebApplication _application;

    @Inject
    RestService(Injector injector, CfgKeyManagersProvider kmgr)
    {
        // NB: at this stage we intentionally do not set the CA cert to allow any clients
        // to connect as it is useful for quick curl tests
        // This will change when the public rest gateway is operational
        super("rest", PORT, kmgr, null);

        _injector = injector;
        _application = WebApplicationFactory.createWebApplication();

        checkNotNull(kmgr.getCert());
        checkNotNull(kmgr.getPrivateKey());
    }

    @Override
    public int start()
    {
        if (!_application.isInitiated()) {
            ResourceConfig cfg = getResourceConfiguration();
            _application.initiate(cfg, new GuiceComponentProviderFactory(cfg, _injector));
        }
        return super.start();
    }

    public void addShutdownHook()
    {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run()
            {
                RestService.this.stop();
            }
        });
    }

    @Override
    protected ChannelPipelineFactory pipelineFactory()
    {
        return new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                return Channels.pipeline(
                        BootstrapFactoryUtil.newSslHandler(_serverSslEngineFactory),
                        new HttpServerCodec(),
                        new ChunkedWriteHandler(),
                        new JerseyHandler(_application));
            }
        };
    }

    private ResourceConfig getResourceConfiguration()
    {
        final Map<String, Object> props = Maps.newHashMap();
        props.put(PackagesResourceConfig.PROPERTY_PACKAGES, RESOURCES_PACKAGES);
        ResourceConfig cfg = new PackagesResourceConfig(props);
        cfg.getFeatures().put(ResourceConfig.FEATURE_DISABLE_WADL, Boolean.TRUE);
        return cfg;
    }
}
