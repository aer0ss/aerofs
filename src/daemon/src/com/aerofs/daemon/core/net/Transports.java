package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.net.link.ILinkStateListener;
import com.aerofs.daemon.core.net.link.LinkStateService;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.CoreIMC;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.event.lib.imc.QueueBasedIMCExecutor;
import com.aerofs.daemon.event.net.EOLinkStateChanged;
import com.aerofs.daemon.lib.IDumpStat;
import com.aerofs.daemon.lib.IDumpStatMisc;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.daemon.mobile.MobileService;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.tcpmt.TCP;
import com.aerofs.daemon.transport.xmpp.XMPP;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import java.io.PrintStream;
import java.net.NetworkInterface;
import java.util.Comparator;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

/**
 * The clients of this class may assume the list of transports never changes during run time.
 */
public class Transports implements IDumpStatMisc, IDumpStat, IStartable
{
    // the more preferred the transport, the smaller value it has.
    public static final Comparator<ITransport> PREFERENCE_COMPARATOR = new Comparator<ITransport>()
    {
        @Override
        public int compare(ITransport arg0, ITransport arg1)
        {
            int comp = arg0.pref() - arg1.pref();
            // transports must have different preferences
            assert arg0 == arg1 || comp != 0;
            return comp;
        }
    };

    private final ITransport[] _tps;

    private final HashMap<ITransport, IIMCExecutor> _tp2imce = Maps.newHashMap();

    private final IIMCExecutor[] _imces;

    final private SortedSet<ITransport> _prefs = new TreeSet<ITransport>(
            Transports.PREFERENCE_COMPARATOR);

    private final CoreQueue _q;
    private final TC _tc;
    private final LinkStateService _lss;

    @Inject
    public Transports(CoreQueue q, TC tc, LinkStateService lss,
            MobileService.Factory mobileServiceFactory)
    {
        _q = q;
        _tc = tc;
        _lss = lss;

        int count = 0;
        if (Cfg.useTCP()) count++;
        if (Cfg.useXMPP()) count++;

        _tps = new ITransport[count];
        _imces = new IIMCExecutor[count];

        // A maxcast filter receiver shared by all transports.
        MaxcastFilterReceiver mcfr = new MaxcastFilterReceiver();
        count = 0;

        if (Cfg.useTCP()) add_(new TCP(_q, mcfr), count++);
        if (Cfg.useXMPP()) add_(new XMPP(_q, mcfr, mobileServiceFactory), count++);
    }

    public ITransport[] getAll_()
    {
        return _tps;
    }

    public IIMCExecutor getIMCE_(ITransport tp)
    {
        return _tp2imce.get(tp);
    }

    private void add_(ITransport tp, int idx)
    {
        Util.l(this).info("add transport " + tp);

        _tps[idx] = tp;
        IIMCExecutor imce = new QueueBasedIMCExecutor(tp.q());
        _tp2imce.put(tp, imce);
        _imces[idx] = imce;
        _prefs.add(tp);

        addLinkStateListener_(tp, imce);
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
                    CoreIMC.enqueueBlocking_(
                            new EOLinkStateChanged(imce, previous, current, added, removed),
                            _tc, Cat.UNLIMITED);
                } catch (Exception e) {
                    Util.l(this).error("failed to enqueue:" + tp.toString());
                }
            }

        // IMPORTANT: I can use sameThreadExecutor because I know that the link-state-changed
        // callback happens on a core thread. See LinkStateService
        }, sameThreadExecutor());
    }

    public void init_()
            throws Exception
    {
        for (ITransport tp : _tps) tp.init_();
    }

    @Override
    public void start_()
    {
        for (ITransport tp : _tps) tp.start_();
    }

    // see also dumpStat()
    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        // because dumpStat on transports may block, we use pseudo pause

        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "dumpStatMisc");
        try {
            TCB tcb = tk.pseudoPause_("dumpStatMisc");
            try {
                for (ITransport tp : _tps) {
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

        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "dumpStat");
        try {
            // according to ITransport's contract dumpStat() may block
            TCB tcb = tk.pseudoPause_("dumpStat");
            try {
                // TODO use core-to-tp events instead
                for (ITransport tp : _tps) tp.dumpStat(template, bd);
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }
}
