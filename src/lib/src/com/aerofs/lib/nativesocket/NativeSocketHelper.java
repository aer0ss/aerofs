/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.nativesocket;

import com.aerofs.lib.OioChannelFactories;
import com.flipkart.phantom.netty.common.OioClientSocketChannelFactory;
import com.flipkart.phantom.netty.common.OioServerSocketChannelFactory;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipelineFactory;

import java.io.File;

public class NativeSocketHelper
{
    public static ServerBootstrap createServerBootstrap(File socketFile,
            ChannelPipelineFactory pipelineFactory)
    {
        // remove any prev created instance of the socketFile if it still exists.
        socketFile.delete();

        OioServerSocketChannelFactory serverSocketChannelFactory =
                OioChannelFactories.getServerChannelFactory();
        serverSocketChannelFactory.setSocketFile(socketFile);
        ServerBootstrap bootstrap = new ServerBootstrap(serverSocketChannelFactory);
        bootstrap.setPipelineFactory(pipelineFactory);
        bootstrap.setOption("SO_REUSEADDR", true);
        return bootstrap;
    }

    public static ClientBootstrap createClientBootstrap(ChannelPipelineFactory pipelineFactory)
    {
        OioClientSocketChannelFactory clientSocketChannelFactory =
                OioChannelFactories.getClientChannelFactory();
        ClientBootstrap bootstrap = new ClientBootstrap(clientSocketChannelFactory);
        bootstrap.setPipelineFactory(pipelineFactory);
        return bootstrap;
    }
}