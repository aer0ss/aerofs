/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.net.TransportFactory.ExUnsupportedTransport;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.IDiagnosable;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.daemon.lib.ITransferStat;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.IMaxcast;
import com.aerofs.daemon.transport.lib.IPresenceSource;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.presence.LocationManager;
import com.aerofs.daemon.transport.ssmp.SSMPConnectionService;
import com.aerofs.daemon.transport.tcp.TCP;
import com.aerofs.daemon.transport.zephyr.Zephyr;
import com.aerofs.daemon.transport.zephyr.ZephyrParams;
import com.aerofs.lib.cfg.*;
import com.aerofs.proto.Diagnostics.TransportDiagnostics;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import java.net.Proxy;
import java.util.Collection;
import java.util.Comparator;

import static com.aerofs.daemon.core.net.TransportFactory.TransportType.LANTCP;
import static com.aerofs.daemon.core.net.TransportFactory.TransportType.ZEPHYR;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * The clients of this class may assume the list of transports never changes during run time.
 */
public class Transports implements IStartable, IDiagnosable, ITransferStat
{
    public static final Comparator<ITransport> PREFERENCE_COMPARATOR = (tp0, tp1) -> {
        int comp = tp0.rank() - tp1.rank();
        checkArgument(tp0 == tp1 || comp != 0,
                "different transports have identical preferences tp0:" + tp0 + " tp1:" + tp1);
        return comp;
    };

    private static final Logger l = Loggers.getLogger(Transports.class);

    private final SSMPConnectionService ssmp;

    private final ImmutableList<IMaxcast> maxcastProviders;
    private final ImmutableList<IPresenceSource> presenceSources;

    private final ImmutableList<ITransport> availableTransports;

    private volatile boolean started = false;

    // FIXME (AG): Inject only the TransportFactory, not all the components to create it
    // NOTE: I probably have to create a TransportModule (Guice) and bind BlockingPrioQueue<IEvent> to CoreQueue
    @Inject
    public Transports(
            CfgLocalUser localid,
            CfgLocalDID localdid,
            CfgEnabledTransports enabledTransports,
            CfgTimeout timeout,
            CfgMulticastLoopback multicastLoopback,
            ZephyrParams zephyrParams,
            Timer timer,
            CoreQueue coreQueue,
            MaxcastFilterReceiver maxcastFilterReceiver,
            LinkStateService linkStateService,
            ClientSSLEngineFactory clientSslEngineFactory,
            ServerSSLEngineFactory serverSslEngineFactory,
            ClientSocketChannelFactory clientSocketChannelFactory,
            ServerSocketChannelFactory serverSocketChannelFactory,
            SSMPConnectionService ssmp,
            LocationManager locationManager,
            IRoundTripTimes roundTripTimes)
            throws ExUnsupportedTransport
    {
        this.ssmp = ssmp;

        TransportFactory transportFactory = new TransportFactory(
                localid.get(),
                localdid.get(),
                timeout.get(),
                multicastLoopback.get(),
                DaemonParam.DEFAULT_CONNECT_TIMEOUT,
                DaemonParam.HEARTBEAT_INTERVAL,
                DaemonParam.MAX_FAILED_HEARTBEATS,
                DaemonParam.Zephyr.HANDSHAKE_TIMEOUT,
                zephyrParams,
                Proxy.NO_PROXY,
                timer,
                coreQueue,
                linkStateService,
                maxcastFilterReceiver,
                clientSocketChannelFactory,
                serverSocketChannelFactory,
                clientSslEngineFactory,
                serverSslEngineFactory,
                roundTripTimes,
                locationManager,
                ssmp);

        ImmutableList.Builder<IMaxcast> maxcastBuilder = ImmutableList.builder();
        ImmutableList.Builder<IPresenceSource> presenceBuilder = ImmutableList.builder();
        ImmutableList.Builder<ITransport> transportBuilder = ImmutableList.builder();

        if (enabledTransports.isTcpEnabled()) {
            TCP tp = (TCP)transportFactory.newTransport(LANTCP);
            transportBuilder.add(tp);
            // FIXME: reaching down to get these fields is gross
            maxcastBuilder.add(tp.multicast);
            presenceBuilder.add(tp.stores);
            locationManager.addPresenceLocationListener(tp.monitor);
        }
        if (enabledTransports.isZephyrEnabled()) {
            Zephyr tp = (Zephyr)transportFactory.newTransport(ZEPHYR);
            transportBuilder.add(tp);

            ssmp.addMulticastListener(tp.monitor);
            locationManager.addPresenceLocationListener(tp.monitor);
            ssmp.addStoreInterestListener(tp.presence);
        }

        ssmp.addMulticastListener(locationManager);

        maxcastBuilder.add(ssmp);
        presenceBuilder.add(ssmp);

        maxcastProviders = maxcastBuilder.build();
        presenceSources = presenceBuilder.build();
        availableTransports = transportBuilder.build();
    }

    public ImmutableList<IMaxcast> maxcastProviders() {
        return maxcastProviders;
    }

    public ImmutableList<IPresenceSource> presenceSources() {
        return presenceSources;
    }

    public Collection<ITransport> getAll()
    {
        return availableTransports;
    }

    public void init_()
            throws Exception
    {
        for (ITransport tp : availableTransports) {
            tp.init();
        }
    }

    @Override
    public void start_()
    {
        l.info("start tps");

        ssmp.start();

        availableTransports.forEach(ITransport::start);

        started = true;
    }

    public void stop_() {
        ssmp.stop();

        availableTransports.forEach(ITransport::stop);

        started = false;
    }

    public boolean started()
    {
        return started;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This method can be called without holding the core lock.
     */
    @Override
    public TransportDiagnostics dumpDiagnostics_()
    {
        TransportDiagnostics.Builder diagnostics = TransportDiagnostics.newBuilder();
        for (ITransport transport : availableTransports) {
            transport.dumpDiagnostics(diagnostics);
        }
        return diagnostics.build();
    }

    @Override
    public long bytesIn()
    {
        long in = 0;
        for (ITransport tp : availableTransports) in += tp.bytesIn();
        return in;
    }

    @Override
    public long bytesOut()
    {
        long out = 0;
        for (ITransport tp : availableTransports) out += tp.bytesOut();
        return out;
    }
}
