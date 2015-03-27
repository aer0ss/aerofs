/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.BaseParam;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.net.TransportFactory.ExUnsupportedTransport;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.IDiagnosable;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.daemon.lib.ITransferStat;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgEnabledTransports;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgScrypted;
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

    private final ImmutableList<ITransport> availableTransports;

    private volatile boolean started = false;

    // FIXME (AG): Inject only the TransportFactory, not all the components to create it
    // NOTE: I probably have to create a TransportModule (Guice) and bind BlockingPrioQueue<IEvent> to CoreQueue
    @Inject
    public Transports(
            CfgLocalUser localid,
            CfgLocalDID localdid,
            CfgScrypted scrypted,
            CfgEnabledTransports enabledTransports,
            Timer timer,
            CoreQueue coreQueue,
            MaxcastFilterReceiver maxcastFilterReceiver,
            LinkStateService linkStateService,
            ClientSSLEngineFactory clientSslEngineFactory,
            ServerSSLEngineFactory serverSslEngineFactory,
            ClientSocketChannelFactory clientSocketChannelFactory,
            ServerSocketChannelFactory serverSocketChannelFactory,
            IRoundTripTimes roundTripTimes)
            throws ExUnsupportedTransport
    {
        TransportFactory transportFactory = new TransportFactory(
                localid.get(),
                localdid.get(),
                scrypted.get(),
                Cfg.timeout(),
                false,
                BaseParam.XMPP.SERVER_ADDRESS,
                BaseParam.XMPP.getServerDomain(),
                5 * C.SEC,
                3,
                LibParam.EXP_RETRY_MIN_DEFAULT,
                LibParam.EXP_RETRY_MAX_DEFAULT,
                DaemonParam.DEFAULT_CONNECT_TIMEOUT,
                DaemonParam.HEARTBEAT_INTERVAL,
                DaemonParam.MAX_FAILED_HEARTBEATS,
                DaemonParam.Zephyr.HANDSHAKE_TIMEOUT,
                BaseParam.Zephyr.SERVER_ADDRESS,
                Proxy.NO_PROXY,
                timer,
                coreQueue,
                linkStateService,
                maxcastFilterReceiver,
                clientSocketChannelFactory,
                serverSocketChannelFactory,
                clientSslEngineFactory,
                serverSslEngineFactory,
                roundTripTimes);

        ImmutableList.Builder<ITransport> bd = ImmutableList.builder();
        if (enabledTransports.isTcpEnabled()) {
            bd.add(transportFactory.newTransport(LANTCP));
        }
        if (enabledTransports.isZephyrEnabled()) {
            bd.add(transportFactory.newTransport(ZEPHYR));
        }
        availableTransports = bd.build();
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

        for (ITransport tp : availableTransports) {
            tp.start();
        }

        started = true;
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
