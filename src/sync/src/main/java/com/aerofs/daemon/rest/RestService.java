/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.daemon.rest;

import com.aerofs.base.net.ISslHandlerFactory;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.rest.resources.AbstractResource;
import com.aerofs.daemon.rest.resources.VersionResource;
import com.aerofs.lib.NioChannelFactories;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.rest.auth.OAuthExtractor;
import com.aerofs.rest.auth.OAuthRequestFilter;
import com.aerofs.rest.providers.*;
import com.aerofs.restless.Service;
import com.aerofs.restless.Version;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.reflect.Reflection;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.sun.jersey.core.spi.scanning.PackageNamesScanner;
import com.sun.jersey.spi.scanning.AnnotationScannerListener;
import com.sun.jersey.spi.scanning.PathProviderScannerListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.Executors.newCachedThreadPool;

public class RestService extends Service
{
    public static final Version HIGHEST_SUPPORTED_VERSION = new Version(1, 2);

    // Port for the service. 0 to use any available port (default)
    // configurable for firewall-friendliness
    private static final int PORT = getIntegerProperty("api.daemon.port", 0);

    private final ISslHandlerFactory _sslHandlerFactory;

    @Inject
    RestService(Injector injector, CfgKeyManagersProvider kmgr, Set<AbstractResource> resources)
    {
        // use a cached thread pool to free-up I/O threads while the requests sit in the core queue
        super("rest", new InetSocketAddress(PORT), injector, newCachedThreadPool());

        _sslHandlerFactory = SSLEngineFactory.newServerFactory(kmgr, null);

        enableVersioning();

        addRequestFilter(OAuthRequestFilter.class);

        checkNotNull(kmgr.getCert());
        checkNotNull(kmgr.getPrivateKey());

        addResource(new AuthProvider(new OAuthExtractor()));
        addResource(VersionResource.class);
        resources.forEach(resource -> addResource(resource));
    }

    @Override
    protected ServerSocketChannelFactory getServerSocketFactory()
    {
        return NioChannelFactories.getServerChannelFactory();
    }

    @Override
    protected ChannelPipelineFactory getPipelineFactory()
    {
        return () -> {
            ChannelPipeline p = getSpecializedPipeline();
            p.addFirst("ssl", _sslHandlerFactory.newSslHandler());
            return p;
        };
    }

    @Override
    protected Set<Class<?>> singletons()
    {
        // TODO reconsider this, as we no longer obfuscate jars.
        // Specify all providers explicitly instead of using a package scanner
        return ImmutableSet.of(
                FactoryReaderProvider.class,
                JsonExceptionMapper.class,
                ParamExceptionMapper.class,
                IllegalArgumentExceptionMapper.class,
                RuntimeExceptionMapper.class
        );
    }
}