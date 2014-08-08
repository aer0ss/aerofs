/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.listeners;

import com.aerofs.base.BaseParam.Verkehr;
import com.aerofs.verkehr.client.rest.VerkehrClient;
import com.google.common.util.concurrent.MoreExecutors;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;

import javax.servlet.ServletContextEvent;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.aerofs.sp.server.lib.SPParam.VERKEHR_CLIENT_ATTRIBUTE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class LogCollectionLifecycleListener extends ConfigurationLifecycleListener
{
    @Override
    public void contextInitialized(ServletContextEvent event)
    {
        super.contextInitialized(event);

        // verkehr
        event.getServletContext().setAttribute(VERKEHR_CLIENT_ATTRIBUTE, createVerkehrClient());
    }

    private static VerkehrClient createVerkehrClient()
    {
        Executor nioExecutor = Executors.newCachedThreadPool();
        NioClientSocketChannelFactory channelFactory = new NioClientSocketChannelFactory(nioExecutor, nioExecutor, 1, 2);
        return VerkehrClient.create(
                Verkehr.HOST,
                Verkehr.REST_PORT,
                MILLISECONDS.convert(30, SECONDS),
                MILLISECONDS.convert(60, SECONDS),
                10,
                new HashedWheelTimer(),
                MoreExecutors.sameThreadExecutor(),
                channelFactory);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event)
    {
        VerkehrClient verkehrClient = (VerkehrClient) event.getServletContext()
                .getAttribute(VERKEHR_CLIENT_ATTRIBUTE);

        if (verkehrClient != null) {
            verkehrClient.disconnectAll();
            verkehrClient.shutdown();
        }

        super.contextDestroyed(event);
    }
}
