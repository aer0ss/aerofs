/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.BaseParam;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.net.link.ILinkStateListener;
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
import com.aerofs.daemon.mobile.MobileServiceFactory;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.tcp.TCP;
import com.aerofs.daemon.transport.xmpp.Jingle;
import com.aerofs.daemon.transport.xmpp.Zephyr;
import com.aerofs.lib.IDumpStat;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.LibParam.EnterpriseConfig;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.Builder;
import com.aerofs.rocklog.RockLog;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.slf4j.Logger;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static com.aerofs.daemon.core.tc.Cat.UNLIMITED;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newLinkedList;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

/**
 * The clients of this class may assume the list of transports never changes during run time.
 */
public class Transports implements IDumpStat, IDumpStatMisc, IStartable
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

    private final ImmutableMap<ITransport, IIMCExecutor> _availableTransports;
    private final TC _tc;
    private final LinkStateService _lss;

    //
    // static transport-construction methods
    //

    private static TCP newTCP(
            String transportId, int transportRank,
            IBlockingPrioritizedEventSink<IEvent> coreQueue,
            MaxcastFilterReceiver mcfr, ClientSocketChannelFactory clientChannelFactory,
            ServerSocketChannelFactory serverChannelFactory)
    {
        return new TCP(transportId, transportRank, coreQueue, mcfr, clientChannelFactory, serverChannelFactory);
    }

    private static Jingle newJingle(
            DID localdid,
            String transportId, int transportRank,
            IBlockingPrioritizedEventSink<IEvent> coreQueue,
            MaxcastFilterReceiver mcfr)
    {
        return new Jingle(localdid, transportId, transportRank, coreQueue, mcfr);
    }

    private static Zephyr newZephyr(UserID localid, DID localdid, String transportId, int transportRank,
            IBlockingPrioritizedEventSink<IEvent> coreQueue,
            MaxcastFilterReceiver mcfr,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            ClientSocketChannelFactory clientChannelFactory,
            MobileServerZephyrConnector mobileServerZephyrConnector, RockLog rocklog,
            InetSocketAddress inetSocketAddress, boolean enableMulticast)
    {
        return new Zephyr(
                localid, localdid,
                transportId,  transportRank,
                coreQueue,
                mcfr,
                clientSslEngineFactory,
                serverSslEngineFactory,
                clientChannelFactory,
                mobileServerZephyrConnector,
                rocklog,
                inetSocketAddress, Proxy.NO_PROXY, enableMulticast);
    }

    @Inject
    public Transports(
            CfgLocalUser localuser, CfgLocalDID localdid,
            CoreQueue coreQueue, TC tc,
            LinkStateService lss,
            MobileServiceFactory mobileServiceFactory,
            RockLog rocklog,
            ClientSocketChannelFactory clientChannelFactory,
            ServerSocketChannelFactory serverSocketChannelFactory)
    {
        _tc = tc;
        _lss = lss;

        MaxcastFilterReceiver mcfr = new MaxcastFilterReceiver(); // shared by all transports

        List<ITransport> transports = newLinkedList();

        if (Cfg.useTCP()) {
            transports.add(newTCP("t", 0, coreQueue, mcfr, clientChannelFactory, serverSocketChannelFactory));
        }
        if (Cfg.useJingle() && !EnterpriseConfig.IS_ENTERPRISE_DEPLOYMENT.get()) {
            transports.add(newJingle(localdid.get(), "j", 1, coreQueue, mcfr));
        }
        if (Cfg.useZephyr()) {
            boolean enableMulticast = !Cfg.useJingle() && Cfg.useZephyr();
            MobileServerZephyrConnector mobileZephyr = new MobileServerZephyrConnector(mobileServiceFactory, clientChannelFactory);
            SSLEngineFactory clientSslEngineFactory = new SSLEngineFactory(Mode.Client, Platform.Desktop, new CfgKeyManagersProvider(), new CfgCACertificateProvider(), null);
            SSLEngineFactory serverSslEngineFactory = new SSLEngineFactory(Mode.Server, Platform.Desktop, new CfgKeyManagersProvider(), new CfgCACertificateProvider(), null);
            transports.add(newZephyr(localuser.get(), localdid.get(), "z", 2, coreQueue, mcfr, clientSslEngineFactory, serverSslEngineFactory, clientChannelFactory, mobileZephyr, rocklog, BaseParam.Zephyr.ADDRESS.get(), enableMulticast));
        }

        ImmutableMap.Builder<ITransport, IIMCExecutor> transportBuilder = ImmutableMap.builder();
        for (ITransport transport: transports) {
            IIMCExecutor imce = new QueueBasedIMCExecutor(transport.q());
            transportBuilder.put(transport, imce);
            addLinkStateListener_(transport, imce);
        }

        this._availableTransports = transportBuilder.build();
    }

    public Collection<ITransport> getAll_()
    {
        return _availableTransports.keySet();
    }

    public IIMCExecutor getIMCE_(ITransport tp)
    {
        return _availableTransports.get(tp);
    }

    private void addLinkStateListener_(final ITransport tp, final IIMCExecutor imce)
    {
        _lss.addListener_(new ILinkStateListener()
        {
            @Override
            public void onLinkStateChanged_(ImmutableSet<NetworkInterface> added,
                    ImmutableSet<NetworkInterface> removed, ImmutableSet<NetworkInterface> current,
                    ImmutableSet<NetworkInterface> previous)
            {
                // TODO (WW) re-run the event if the transport failed to handle it. the solution
                // would be easier once we converted events to Futures.
                try {
                    l.info("notify tps of lsc");
                    CoreIMC.enqueueBlocking_(new EOLinkStateChanged(imce, previous, current, added, removed), _tc, UNLIMITED);
                    l.info("complete notify tps of lsc");
                } catch (Exception e) {
                    l.error("failed to enqueue:" + tp.toString());
                }
            }

        // IMPORTANT: I can use sameThreadExecutor because I know that the link-state-changed
        // callback happens on a core thread. See LinkStateService
        }, sameThreadExecutor());
    }

    public void init_()
            throws Exception
    {
        for (ITransport tp : _availableTransports.keySet()) {
            tp.init_();
        }
    }

    @Override
    public void start_()
    {
        l.info("start all tps");

        for (ITransport tp : _availableTransports.keySet()) {
            tp.start_();
        }
    }

    // see also dumpStat()
    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        // because dumpStat on transports may block, we use pseudo pause

        Token tk = _tc.acquireThrows_(UNLIMITED, "dumpStatMisc");
        try {
            TCB tcb = tk.pseudoPause_("dumpStatMisc");
            try {
                for (ITransport tp : _availableTransports.keySet()) {
                    ps.println(indent + tp.id());

                    // TODO use core-to-tp events instead
                    tp.dumpStatMisc(indent + indentUnit, indentUnit, ps);
                }
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }

    // see also dumpStatMisc()
    @Override
    public void dumpStat(PBDumpStat template, Builder bd)
            throws Exception
    {
        // because dumpStat on transports may block, we use pseudo pause

        Token tk = _tc.acquireThrows_(UNLIMITED, "dumpStat");
        try {
            // according to ITransport's contract dumpStat() may block
            TCB tcb = tk.pseudoPause_("dumpStat");
            try {
                for (ITransport tp : _availableTransports.keySet()) {
                    bd.addEnabledTransports(tp.id());
                    tp.dumpStat(template, bd); // TODO use core-to-tp events instead
                }
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }
}
