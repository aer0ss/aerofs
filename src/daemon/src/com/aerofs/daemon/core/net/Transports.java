/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.BaseParam;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
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
import com.aerofs.daemon.transport.tcpmt.TCP;
import com.aerofs.daemon.transport.xmpp.Jingle;
import com.aerofs.daemon.transport.xmpp.Zephyr;
import com.aerofs.lib.IDumpStat;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.slf4j.Logger;

import java.io.PrintStream;
import java.net.NetworkInterface;
import java.util.Collection;
import java.util.Comparator;

import static com.aerofs.daemon.core.tc.Cat.UNLIMITED;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

/**
 * The clients of this class may assume the list of transports never changes during run time.
 */
public class Transports implements IDumpStat, IDumpStatMisc, IStartable
{
    private static final Logger l = Loggers.getLogger(Transports.class);

    public static interface ITransportImplementation
    {
        ITransport newTransport_(DID localdid, IBlockingPrioritizedEventSink<IEvent> q, MaxcastFilterReceiver mcfr);

        boolean isEnabled();
    }

    public static enum TransportImplementation implements ITransportImplementation
    {
        TCPBMT("t")
        {
            @Override
            public ITransport newTransport_(DID localdid, IBlockingPrioritizedEventSink<IEvent> q, MaxcastFilterReceiver mcfr)
            {
                return new TCP(id(), rank(), q, mcfr);
            }

            @Override
            public boolean isEnabled()
            {
                return Cfg.useTCP();
            }
        },
        JINGLE("j")
        {
            @Override
            public ITransport newTransport_(DID localdid, IBlockingPrioritizedEventSink<IEvent> q, MaxcastFilterReceiver mcfr)
            {
                return new Jingle(localdid, id(), rank(), q, mcfr);
            }

            @Override
            public boolean isEnabled()
            {
                return Cfg.useJingle();
            }
        },
        ZEPHYR(BaseParam.Zephyr.TRANSPORT_ID)
        {
            @Override
            public ITransport newTransport_(DID localdid, IBlockingPrioritizedEventSink<IEvent> q, MaxcastFilterReceiver mcfr)
            {
                boolean enableMulticast = !Cfg.useJingle() && Cfg.useZephyr();
                return new Zephyr(localdid, id(),  rank(), q, mcfr, enableMulticast);
            }

            @Override
            public boolean isEnabled()
            {
                return Cfg.useZephyr();
            }
        },
        NOOPTP("n") // FIXME (AG): remove this!
        {
            @Override
            public ITransport newTransport_(DID localdid, IBlockingPrioritizedEventSink<IEvent> q, MaxcastFilterReceiver mcfr)
            {
                throw new UnsupportedOperationException("cannot make an instance of this transport");
            }

            @Override
            public boolean isEnabled()
            {
                return false;
            }
        }; // this must _always_ be last

        private final String _id;

        private TransportImplementation(String id)
        {
            _id = id;
        }

        public String id()
        {
            return _id;
        }

        public int rank()
        {
            return ordinal();
        }

        @Override
        public String toString()
        {
            return _id;
        }
    }

    // the more preferred the transport, the smaller value it has.
    public static final Comparator<ITransport> PREFERENCE_COMPARATOR = new Comparator<ITransport>()
    {
        @Override
        public int compare(ITransport tp0, ITransport tp1)
        {
            int comp = tp0.rank() - tp1.rank();
            checkArgument(tp0 == tp1 || comp != 0,
                    "different transports have identical preferences tp0:" + tp0 + " tp1:" + tp1);
            return comp;
        }
    };

    private final ImmutableMap<ITransport, IIMCExecutor> _availableTransports;
    private final TC _tc;
    private final LinkStateService _lss;

    @Inject
    public Transports(CfgLocalDID localdid, CoreQueue q, TC tc, LinkStateService lss,
            MobileServiceFactory mobileServiceFactory, ClientSocketChannelFactory clientChannelFactory)
    {
        this._tc = tc;
        this._lss = lss;

        MaxcastFilterReceiver mcfr = new MaxcastFilterReceiver(); // shared by all transports

        ImmutableMap.Builder<ITransport, IIMCExecutor> transportBuilder = ImmutableMap.builder();

        for (TransportImplementation i : TransportImplementation.values()) {
            if (i.isEnabled()) {
                ITransport tp = i.newTransport_(localdid.get(), q, mcfr);

                // [sigh] hack because the enums assume that all transports take the same params
                // FIXME (AG): revert to the old style of construction without enums
                // FIXME (AG): I should have MobileServerZephyrConnector connect directly to XmppServerConnection
                if (i == TransportImplementation.ZEPHYR) {
                    ((Zephyr) tp).setMobileServerZephyrConnector(
                            new MobileServerZephyrConnector(
                                    mobileServiceFactory, clientChannelFactory));
                }

                l.info("add transport " + tp);

                IIMCExecutor imce = new QueueBasedIMCExecutor(tp.q());
                transportBuilder.put(tp, imce);
                addLinkStateListener_(tp, imce);
            }
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
                    CoreIMC.enqueueBlocking_(
                            new EOLinkStateChanged(imce, previous, current, added, removed), _tc,
                            UNLIMITED);
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
