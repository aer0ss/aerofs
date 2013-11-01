/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.daemon.rest;

import com.aerofs.daemon.rest.providers.OAuthProvider;
import com.aerofs.daemon.rest.resources.ChildrenResource;
import com.aerofs.daemon.rest.resources.FilesResource;
import com.aerofs.daemon.rest.resources.FoldersResource;
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

public class RestService extends Service
{
    public static final String VERSION = "/v0.9";

    // Port for the service. 0 to use any available port (default)
    // configurable for firewall-friendliness
    private static final int PORT = getIntegerProperty("api.daemon.port", 0);

    @Inject
    RestService(Injector injector, CfgKeyManagersProvider kmgr)
    {
        super("rest", new InetSocketAddress(PORT), kmgr, injector);

        checkNotNull(kmgr.getCert());
        checkNotNull(kmgr.getPrivateKey());
    }

    @Override
    protected ServerSocketChannelFactory getServerSocketFactory()
    {
        return ChannelFactories.getServerChannelFactory();
    }

    @Override
    protected Set<Class<?>> singletons()
    {
        // specify all providers and resources explictly instead of using a package scanner
        // because we flatten packages in the proguard step
        return ImmutableSet.of(
                ChildrenResource.class,
                FoldersResource.class,
                FilesResource.class,
                OAuthProvider.class
        );
    }
}
