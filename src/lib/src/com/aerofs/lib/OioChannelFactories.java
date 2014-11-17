/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib;

import com.aerofs.base.Lazy;
import com.flipkart.phantom.netty.common.OioClientSocketChannelFactory;
import com.flipkart.phantom.netty.common.OioServerSocketChannelFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.Executors;

public class OioChannelFactories
{
    private static Lazy<OioServerSocketChannelFactory> _serverSocketChannelFactory =
            new Lazy<>(() -> {
                return new OioServerSocketChannelFactory(Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool());
            });

    private static  Lazy<OioClientSocketChannelFactory> _clientSocketChannelFactory =
            new Lazy<>(() -> {
                return new OioClientSocketChannelFactory(Executors.newCachedThreadPool());
            });

    public static OioServerSocketChannelFactory getServerChannelFactory()
    {
        return _serverSocketChannelFactory.get();
    }

    public static OioClientSocketChannelFactory getClientChannelFactory()
    {
        return _clientSocketChannelFactory.get();
    }
}
