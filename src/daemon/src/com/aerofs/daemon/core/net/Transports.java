/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.BaseParam;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.net.TransportFactory.ExUnsupportedTransport;
import com.aerofs.daemon.core.net.link.ILinkStateListener;
import com.aerofs.daemon.core.net.link.ILinkStateService;
import com.aerofs.daemon.core.net.link.LinkStateService;
import com.aerofs.daemon.core.tc.CoreIMC;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.event.lib.imc.QueueBasedIMCExecutor;
import com.aerofs.daemon.event.net.EOLinkStateChanged;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.daemon.mobile.MobileServerZephyrConnector;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.xmpp.Zephyr;
import com.aerofs.lib.IDumpStat;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.ITransferStat;
import com.aerofs.lib.LibParam.EnterpriseConfig;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsRTRoot;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgScrypted;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.Builder;
import com.aerofs.rocklog.RockLog;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.slf4j.Logger;

import java.io.PrintStream;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

import static com.aerofs.daemon.core.net.TransportFactory.Transport.JINGLE;
import static com.aerofs.daemon.core.net.TransportFactory.Transport.LANTCP;
import static com.aerofs.daemon.core.net.TransportFactory.Transport.ZEPHYR;
import static com.aerofs.daemon.core.tc.Cat.UNLIMITED;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

/**
 * The clients of this class may assume the list of transports never changes during run time.
 */
public class Transports implements IDumpStat, IDumpStatMisc, IStartable, ITransferStat
{
    public static final Comparator<ITransport> PREFERENCE_COMPARATOR = new Comparator<ITransport>()
    {
        @Override
        public int compare(ITransport tp0, ITransport tp1)
        {
            int comp = tp0.rank() - tp1.rank();
            checkArgument(tp0 == tp1 || comp != 0, "different transports have identical preferences tp0:" + tp0 + " tp1:" + tp1);
            return comp;
        }
    };

    private static final Logger l = Loggers.getLogger(Transports.class);

    private final Map<ITransport, IIMCExecutor> availableTransports = newHashMap();
    private final TC tc;

    @Inject
    public Transports(
            CfgAbsRTRoot absRTRoot,
            CfgLocalUser localid,
            CfgLocalDID localdid,
            CfgScrypted scrypted,
            CoreQueue coreQueue,
            TC tc,
            MaxcastFilterReceiver maxcastFilterReceiver,
            LinkStateService linkStateService,
            MobileServerZephyrConnector mobileServerZephyrConnector,
            RockLog rocklog,
            ClientSSLEngineFactory clientSslEngineFactory,
            ServerSSLEngineFactory serverSslEngineFactory,
            ClientSocketChannelFactory clientSocketChannelFactory,
            ServerSocketChannelFactory serverSocketChannelFactory)
            throws ExUnsupportedTransport
    {
        this.tc = tc;

        TransportFactory transportFactory = new TransportFactory(
                absRTRoot,
                localid,
                localdid,
                scrypted,
                BaseParam.Zephyr.ADDRESS.get(),
                Proxy.NO_PROXY,
                coreQueue,
                rocklog,
                maxcastFilterReceiver,
                mobileServerZephyrConnector,
                clientSocketChannelFactory,
                serverSocketChannelFactory,
                clientSslEngineFactory,
                serverSslEngineFactory);

        if (Cfg.useTCP()) {
            addTransport(transportFactory.newTransport(LANTCP), linkStateService);
        }
        if (Cfg.useJingle() && !EnterpriseConfig.IS_ENTERPRISE_DEPLOYMENT.get()) {
            addTransport(transportFactory.newTransport(JINGLE), linkStateService);
        }
        if (Cfg.useZephyr()) {
            Zephyr zephyr = (Zephyr) transportFactory.newTransport(ZEPHYR);
            if (!Cfg.useJingle()) zephyr.enableMulticast();
            addTransport(zephyr, linkStateService);
        }
    }

    private void addTransport(ITransport transport, ILinkStateService linkStateService)
    {
        IIMCExecutor imce = new QueueBasedIMCExecutor(transport.q());
        addLinkStateListener(transport, imce, linkStateService);
        availableTransports.put(transport, imce);
    }

    public Collection<ITransport> getAll_()
    {
        return availableTransports.keySet();
    }

    public IIMCExecutor getIMCE_(ITransport tp)
    {
        return availableTransports.get(tp);
    }

    private void addLinkStateListener(ITransport transport, final IIMCExecutor transportImce, ILinkStateService linkStateService)
    {
        final String transportId = transport.id();

        linkStateService.addListener_(new ILinkStateListener()
        {
            @Override
            public void onLinkStateChanged_(ImmutableSet<NetworkInterface> added, ImmutableSet<NetworkInterface> removed, ImmutableSet<NetworkInterface> current, ImmutableSet<NetworkInterface> previous)
            {
                try {
                    l.info("notify lsc {}", transportId);
                    CoreIMC.enqueueBlocking_( new EOLinkStateChanged(transportImce, previous, current, added, removed), tc, UNLIMITED); // TODO (WW) re-run if transport fails to handle event
                } catch (Exception e) {
                    l.error("fail notify lsc {}", transportId);
                }
            }
        }, sameThreadExecutor()); // IMPORTANT: can use sameThreadExecutor because lsc callback happens on core thread. See LinkStateService
    }

    public void init_()
            throws Exception
    {
        for (ITransport tp : availableTransports.keySet()) {
            tp.init_();
        }
    }

    @Override
    public void start_()
    {
        l.info("start tps");

        for (ITransport tp : availableTransports.keySet()) {
            tp.start_();
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        Token tk = tc.acquireThrows_(UNLIMITED, "dumpStatMisc"); // because dumpStat on transports may block, we use pseudo pause
        try {
            TCB tcb = tk.pseudoPause_("dumpStatMisc");
            try {
                for (ITransport tp : availableTransports.keySet()) {
                    ps.println(indent + tp.id());
                    tp.dumpStatMisc(indent + indentUnit, indentUnit, ps);
                }
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }

    @Override
    public void dumpStat(PBDumpStat template, Builder bd)
            throws Exception
    {
        Token tk = tc.acquireThrows_(UNLIMITED, "dumpStat"); // because dumpStat on transports may block, we use pseudo pause
        try {
            TCB tcb = tk.pseudoPause_("dumpStat"); // according to ITransport's contract dumpStat() may block
            try {
                for (ITransport tp : availableTransports.keySet()) {
                    bd.addEnabledTransports(tp.id());
                    tp.dumpStat(template, bd);
                }
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }

    @Override
    public long bytesIn()
    {
        long in = 0;
        for (ITransport tp : availableTransports.keySet()) in += tp.bytesIn();
        return in;
    }

    @Override
    public long bytesOut()
    {
        long out = 0;
        for (ITransport tp : availableTransports.keySet()) out += tp.bytesOut();
        return out;
    }
}
