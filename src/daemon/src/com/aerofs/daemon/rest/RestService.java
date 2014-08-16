/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.daemon.rest;

import com.aerofs.base.net.ISslHandlerFactory;
import com.aerofs.restless.Version;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.rest.providers.FactoryReaderProvider;
import com.aerofs.rest.providers.IllegalArgumentExceptionMapper;
import com.aerofs.rest.providers.ParamExceptionMapper;
import com.aerofs.rest.providers.JsonExceptionMapper;
import com.aerofs.rest.providers.OAuthProvider;
import com.aerofs.rest.providers.RuntimeExceptionMapper;
import com.aerofs.daemon.rest.resources.ChildrenResource;
import com.aerofs.daemon.rest.resources.FilesResource;
import com.aerofs.daemon.rest.resources.FoldersResource;
import com.aerofs.daemon.rest.resources.VersionResource;
import com.aerofs.lib.ChannelFactories;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.restless.Service;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;

import java.net.InetSocketAddress;
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
    RestService(Injector injector, CfgKeyManagersProvider kmgr)
    {
        // use a cached thread pool to free-up I/O threads while the requests sit in the core queue
        super("rest", new InetSocketAddress(PORT), injector, newCachedThreadPool());

        _sslHandlerFactory = SSLEngineFactory.newServerFactory(kmgr, null);

        enableVersioning();

        checkNotNull(kmgr.getCert());
        checkNotNull(kmgr.getPrivateKey());

        addResource(VersionResource.class);
        addResource(ChildrenResource.class);
        addResource(FoldersResource.class);
        addResource(FilesResource.class);
    }

    @Override
    protected ServerSocketChannelFactory getServerSocketFactory()
    {
        return ChannelFactories.getServerChannelFactory();
    }

    @Override
    protected ChannelPipelineFactory getPipelineFactory()
    {
        return new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                ChannelPipeline p = getSpecializedPipeline();
                p.addFirst("ssl", _sslHandlerFactory.newSslHandler());
                return p;
            }
        };
    }

    @Override
    protected Set<Class<?>> singletons()
    {
        // specify all providers explictly instead of using a package scanner
        // because we flatten packages in the proguard step
        return ImmutableSet.<Class<?>>of(
                OAuthProvider.class,
                FactoryReaderProvider.class,
                JsonExceptionMapper.class,
                ParamExceptionMapper.class,
                IllegalArgumentExceptionMapper.class,
                RuntimeExceptionMapper.class
        );
    }
}
