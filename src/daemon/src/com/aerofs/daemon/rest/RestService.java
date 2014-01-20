/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.daemon.rest;

import com.aerofs.base.Version;
import com.aerofs.daemon.rest.providers.IllegalArgumentExceptionMapper;
import com.aerofs.daemon.rest.providers.ParamExceptionMapper;
import com.aerofs.daemon.rest.providers.JsonExceptionMapper;
import com.aerofs.daemon.rest.providers.OAuthProvider;
import com.aerofs.daemon.rest.providers.RuntimeExceptionMapper;
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
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;

import java.net.InetSocketAddress;
import java.util.Set;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.Executors.newCachedThreadPool;

public class RestService extends Service
{
    public static final Version HIGHEST_SUPPORTED_VERSION = new Version(0, 10);

    // Port for the service. 0 to use any available port (default)
    // configurable for firewall-friendliness
    private static final int PORT = getIntegerProperty("api.daemon.port", 0);

    @Inject
    RestService(Injector injector, CfgKeyManagersProvider kmgr)
    {
        // use a cached thread pool to free-up I/O threads while the requests sit in the core queue
        super("rest", new InetSocketAddress(PORT), kmgr, injector, newCachedThreadPool());

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
    protected Set<Class<?>> singletons()
    {
        // specify all providers explictly instead of using a package scanner
        // because we flatten packages in the proguard step
        return ImmutableSet.of(
                OAuthProvider.class,
                JsonExceptionMapper.class,
                ParamExceptionMapper.class,
                IllegalArgumentExceptionMapper.class,
                RuntimeExceptionMapper.class
        );
    }
}
