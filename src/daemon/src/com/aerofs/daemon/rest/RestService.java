/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.daemon.rest;

import com.aerofs.base.Version;
import com.aerofs.daemon.rest.providers.OAuthProvider;
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
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.google.common.base.Preconditions.checkNotNull;

public class RestService extends Service
{
    public static final Version HIGHEST_SUPPORTED_VERSION = new Version(0, 10);

    // For now accept any version,
    public static final String VERSION = "/v{version: [0-9]+\\.[0-9]+}";

    // Port for the service. 0 to use any available port (default)
    // configurable for firewall-friendliness
    private static final int PORT = getIntegerProperty("api.daemon.port", 0);

    // use a cached thread pool to free-up I/O threads while the requests sit in the core queue
    private final Executor _executor = Executors.newCachedThreadPool();

    @Inject
    RestService(Injector injector, CfgKeyManagersProvider kmgr)
    {
        super("rest", new InetSocketAddress(PORT), kmgr, injector);

        enableVersioning();

        checkNotNull(kmgr.getCert());
        checkNotNull(kmgr.getPrivateKey());
    }

    @Override
    protected ServerSocketChannelFactory getServerSocketFactory()
    {
        return ChannelFactories.getServerChannelFactory();
    }

    @Override
    public ChannelPipeline getSpecializedPipeline()
    {
        ChannelPipeline p = super.getSpecializedPipeline();
        p.addBefore("jersey", "exec", new ExecutionHandler(_executor));
        return p;
    }


    @Override
    protected Set<Class<?>> singletons()
    {
        // specify all providers and resources explictly instead of using a package scanner
        // because we flatten packages in the proguard step
        return ImmutableSet.of(
                VersionResource.class,
                ChildrenResource.class,
                FoldersResource.class,
                FilesResource.class,
                OAuthProvider.class
        );
    }
}
