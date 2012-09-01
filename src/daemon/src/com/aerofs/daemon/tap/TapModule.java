/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap;

import com.aerofs.daemon.core.net.link.ILinkStateService;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.base.EventQueueBasedEventLoop;
import com.aerofs.daemon.tng.base.http.Proxies;
import com.aerofs.lib.Param.Zephyr;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.DID;
import com.aerofs.proto.Tap.ITapService;
import com.google.inject.AbstractModule;
import com.google.inject.Stage;
import org.apache.log4j.Logger;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;

import static com.aerofs.daemon.lib.DaemonParam.QUEUE_LENGTH_DEFAULT;

public class TapModule extends AbstractModule
{
    private static final Logger l = Util.l(TapModule.class);

    @Override
    protected void configure()
    {
        // Required to pick-up proxy settings
        System.setProperty("java.net.useSystemProxies", "true");

        EventQueueBasedEventLoop executor = new EventQueueBasedEventLoop(
                new BlockingPrioQueue<IEvent>(QUEUE_LENGTH_DEFAULT));

        // All singletons here
        // Change the Zephyr host and port if you want to test with your own Zephyr relay
        bind(InetSocketAddress.class).annotatedWith(TransportFactory.ZephyrAddress.class)
                .toInstance(
                        new InetSocketAddress(Zephyr.zephyrHost(), Zephyr.zephyrPort()));
        bind(EventQueueBasedEventLoop.class).toInstance(executor);
        bind(ISingleThreadedPrioritizedExecutor.class).toInstance(executor);
        bind(DID.class).annotatedWith(TransportFactory.LocalDID.class).toInstance(Cfg.did());

        Proxy proxy = Proxies.getSystemProxy(Type.HTTP, "aerofs.com");
        if (!proxy.equals(Proxy.NO_PROXY)) {
            l.info("using proxy: " + proxy);
        }
        bind(Proxy.class).toInstance(proxy);

        // These are not singletons, aka, new objects on each request
        bind(ILinkStateService.class).to(EventLoopBasedLinkStateService.class);

        if (currentStage() == Stage.PRODUCTION) {
            bind(ITapService.class).to(TapServiceImpl.class);
        } else {
            bind(ITapService.class).to(MockTapServiceImpl.class);
        }
    }

}
