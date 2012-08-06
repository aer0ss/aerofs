package com.aerofs.daemon.core.net.tng;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.net.CoreQueueBasedTransportListener;
import com.aerofs.daemon.core.net.PeerStreamMap;
import com.aerofs.daemon.core.net.PeerStreamMap.IncomingStreamMap;
import com.aerofs.daemon.core.net.link.LinkStateMonitor;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.IDebug;
import com.aerofs.daemon.tng.IIncomingStream;
import com.aerofs.daemon.tng.ITransport;
import com.aerofs.daemon.tng.ReceivedMaxcastFilter;
import com.aerofs.daemon.tng.base.BasePipelineFactory;
import com.aerofs.daemon.tng.base.EventQueueBasedEventLoop;
import com.aerofs.daemon.tng.base.pipeline.IPipelineFactory;
import com.aerofs.daemon.tng.diagnosis.PeerDiagnoser;
import com.aerofs.daemon.tng.xmpp.XMPPBasedTransportFactory;
import com.aerofs.lib.Param.Zephyr;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.inject.Inject;
import static com.aerofs.daemon.core.net.tng.Transports.Preferences.*;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Proxy;

import static com.aerofs.daemon.lib.DaemonParam.QUEUE_LENGTH_DEFAULT;
import static com.aerofs.daemon.tng.ITransport.DEFAULT_COMPARATOR;

/** The clients of this class may assume the list of transports never changes during run time. */
public class Transports implements IDebug
{
    public static enum Preferences
    {
        TCPMLT("t"),
        JINGLE("j"),
        ZEPHYR("z");

        private Preferences(String id)
        {
            _id = id;
        }

        public String id()
        {
            return _id;
        }

        public Preference pref()
        {
            return new Preference(ordinal());
        }

        private final String _id;
    }

    private final TC _tc;
    private final ImmutableSortedSet<ITransport> _transports;

    @Inject
    public Transports(TC tc,
            CoreQueue coreQueue,
            LinkStateMonitor networkLinkStateService,
            @IncomingStreamMap PeerStreamMap<IIncomingStream> streamMap)
    {
        _tc = tc;

        EventQueueBasedEventLoop transportEventLoop = new EventQueueBasedEventLoop(
                new BlockingPrioQueue<IEvent>(QUEUE_LENGTH_DEFAULT));
        ImmutableSortedSet.Builder<ITransport> transportsBuilder = new ImmutableSortedSet.Builder<ITransport>(
                DEFAULT_COMPARATOR);
        ReceivedMaxcastFilter receivedMaxcastFilter = new ReceivedMaxcastFilter();
        PeerDiagnoser peerDiagnoser = new PeerDiagnoser();

        Proxy proxy = Proxy.NO_PROXY; // FIXME <-- need to actually get proxy information

        if (Cfg.useTCP()) { /* FIXME: noop for now */ }

        if (Cfg.useJingle() || Cfg.useZephyr()) {
            XMPPBasedTransportFactory xmppTransportsFactory = new XMPPBasedTransportFactory(
                    Cfg.did(), proxy, transportEventLoop, peerDiagnoser, networkLinkStateService,
                    receivedMaxcastFilter);

            if (Cfg.useJingle()) {
                // FIXME: The CoreQueueBasedTransportListener requires an ITransport, but there is a cyclic dependency in that
                // the ITransport needs a ITransportListener. So we fix this by adding a setTransport method to the CoreQueueBasedListener,
                // cast the ITransportListener we created to its concrete type, and set the transport after it was created
                CoreQueueBasedTransportListener listener = new CoreQueueBasedTransportListener(transportEventLoop, coreQueue, streamMap);
                IPipelineFactory pipelineFactory = new BasePipelineFactory(transportEventLoop, listener);

                ITransport transport = xmppTransportsFactory.createJingle_(JINGLE.id(), JINGLE.pref(), listener, pipelineFactory);
                listener.setTransport(transport);
                transportsBuilder.add(transport);
            }
            if (Cfg.useZephyr()) {
                // FIXME: The CoreQueueBasedTransportListener requires an ITransport, but there is a cyclic dependency in that
                // the ITransport needs a ITransportListener. So we fix this by adding a setTransport method to the CoreQueueBasedListener,
                // cast the ITransportListener we created to its concrete type, and set the transport after it was created
                CoreQueueBasedTransportListener listener = new CoreQueueBasedTransportListener(transportEventLoop, coreQueue, streamMap);
                InetSocketAddress zephyrAddress = new InetSocketAddress(
                        Zephyr.zephyrHost(), Zephyr.zephyrPort());
                IPipelineFactory pipelineFactory = new BasePipelineFactory(transportEventLoop, listener);

                ITransport transport = xmppTransportsFactory.createZephyr_(ZEPHYR.id(), ZEPHYR.pref(), zephyrAddress, listener, pipelineFactory);
                listener.setTransport(transport);
                transportsBuilder.add(transport);
            }
        }

        _transports = transportsBuilder.build();
    }

    public ImmutableSet<ITransport> getAllTransports_()
    {
        return _transports;
    }

    public void start_() throws Exception
    {
        for (ITransport transport : _transports) {
            transport.start_();
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        // because dumpStat on transports may block, we use pseudo pause

        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "dumpStatMisc");
        try {
            TCB tcb = tk.pseudoPause_("dumpStatMisc");
            try {
                for (ITransport transport : _transports) {
                    ps.println(indent + transport.id());
                    transport.dumpStatMisc(indent + indentUnit, indentUnit, ps);
                }
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }

    @Override
    public void dumpStat(PBDumpStat template, Builder builder)
            throws Exception
    {
        // because dumpStat on transports may block, we use pseudo pause

        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "dumpStat");
        try {
            // according to ITransport's contract dumpStat() may block
            TCB tcb = tk.pseudoPause_("dumpStat");
            try {
                for (ITransport transport : _transports) {
                    transport.dumpStat(template, builder);
                }
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }
}
