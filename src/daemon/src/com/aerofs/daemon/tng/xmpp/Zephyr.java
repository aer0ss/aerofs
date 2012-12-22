/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp;

import com.aerofs.daemon.core.net.link.ILinkStateService;
import com.aerofs.daemon.core.net.tng.Preference;
import com.aerofs.daemon.tng.IPeerDiagnoser;
import com.aerofs.daemon.tng.ITransport;
import com.aerofs.daemon.tng.ITransportListener;
import com.aerofs.daemon.tng.base.AbstractTransport;
import com.aerofs.daemon.tng.base.IEventLoop;
import com.aerofs.daemon.tng.base.IMaxcastService;
import com.aerofs.daemon.tng.base.INetworkStats;
import com.aerofs.daemon.tng.base.IPresenceService;
import com.aerofs.daemon.tng.base.IUnicastService;
import com.aerofs.daemon.tng.base.UnicastService;
import com.aerofs.daemon.tng.base.pipeline.IPipelineFactory;
import com.aerofs.daemon.tng.xmpp.zephyr.ZephyrUnicastConnectionService;
import com.aerofs.base.id.DID;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.Executors;

import static com.aerofs.daemon.lib.DaemonParam.Zephyr.WORKER_THREAD_POOL_SIZE;

final class Zephyr extends AbstractTransport
{
    private Zephyr(String id, Preference pref, IEventLoop eventLoop,
            IPresenceService presenceService, IUnicastService unicastService,
            IMaxcastService maxcastService)
    {
        super(id, pref, eventLoop, presenceService, unicastService, maxcastService);
    }

    public static ITransport getInstance_(IEventLoop eventLoop, String id, Preference pref,
            DID localdid, Proxy proxy, IPipelineFactory pipelineFactory,
            InetSocketAddress zephyrAddress, ITransportListener transportListener,
            IPeerDiagnoser peerDiagnoser, ILinkStateService networkLinkStateService,
            IPresenceService presenceService, IMaxcastService maxcastService,
            ISignallingService signallingService)
    {
        ChannelFactory channelFactory = new NioClientSocketChannelFactory(
                Executors.newSingleThreadExecutor(),
                Executors.newFixedThreadPool(WORKER_THREAD_POOL_SIZE));

        ZephyrUnicastConnectionService unicastConnectionService = ZephyrUnicastConnectionService.getInstance_(
                eventLoop, localdid, zephyrAddress, proxy, networkLinkStateService,
                signallingService, channelFactory, new INetworkStats.BasicStatsCounter());

        UnicastService unicastService = UnicastService.getInstance_(eventLoop,
                networkLinkStateService, presenceService, unicastConnectionService,
                pipelineFactory);

        return new Zephyr(id, pref, eventLoop, presenceService, unicastService, maxcastService);
    }
}
