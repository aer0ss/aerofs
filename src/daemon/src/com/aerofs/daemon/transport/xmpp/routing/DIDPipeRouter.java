/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp.routing;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.lib.sched.IScheduler;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.transport.xmpp.IPipe;
import com.aerofs.lib.OutArg;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.*;

import static com.aerofs.daemon.lib.DaemonParam.QUEUE_LENGTH_DEFAULT;
import static com.aerofs.daemon.lib.DaemonParam.XMPP.CONNECT_TIMEOUT;
import static com.aerofs.daemon.transport.lib.IIdentifier.DefaultComparator;
import static com.aerofs.daemon.transport.xmpp.routing.ErrorPipe.ERROR_PIPE;
import static com.aerofs.daemon.transport.xmpp.routing.NoopWaiter.NOOP_WAITER;
import static java.util.Collections.unmodifiableSortedMap;

/**
 * Represents the state of a single logical connected pipe - which may
 * contain multiple underlying {@link IPipe} objects - to a peer represented
 * by a {@link DID}. This implementation makes the following important
 * assumptions:
 * <ol>
 *      <li>Each 'set' of connect requests (one per configured pipe) is
 *          represented by a single <em>connection attempt sequence
 *          number.</em></li>
 *      <li>We use <em>one</em> timeout by which times one of the connect requests
 *          in the set succeed or all fail - <strong>THIS IS CRITICALLY IMPORTANT -
 *          DO NOT CHANGE THIS ASSUMPTION BEFORE THINKING THROUGH THE
 *          CONSEQUENCES.</strong> This is important because if each configured
 *          pipe has a different timeout, when a timeout takes place you can
 *          never tell if there are other pipes who haven't timed out yet
 *          in your 'set'. (Hmm...I guess we could have a pipe counter...i.e. how
 *          many pipes are left in this connect set.) At any rate, if the former
 *          assumption is the case you'll never be able to flush the queue of
 *          stalled packets.</li>
 *      <li>Once we perform a 'set' of connect requests we wait until <em>at least
 *          one succeeds, or all fail, before starting a second 'set'.</li>
 *      <li>Two flags have to be set when initiating this 'set':
 *          <strong>_connecting</strong> and <strong>curRouteSeqNum</strong> for
 *          the affected {@link com.aerofs.daemon.transport.xmpp.IPipe}.</li>
 * </ol>
 */
// FIXME: I don't like that I had to generify this
class DIDPipeRouter<T extends IPipe>
{
    /**
     * Constructor
     *
     * @param did {@link DID} of the peer for which we represet the logical pipe
     * @param sched {@link IScheduler} used to schedule connect 'set' timeouts
     * @param available {@link IPipe} objects that can be used to connect to peers
     */
    DIDPipeRouter(DID did, IScheduler sched, Set<T> available)
    {
        assert did != null && sched != null && available != null && !available.isEmpty() : ("invalid args");

        _did = did;
        _pream = "dpr: d:" + _did;
        _sched = sched;
        _connected.add(ERROR_PIPE);
        _available = unmodifiableSortedMap(makedpccs(available));

        assert bestAvailablePref_() < worstPossiblePref(): (_pream + " no pipes configured");
    }

    /**
     *
     * @param p
     */
    void peerConnected_(IPipe p)
    {
        l.info(_pream + " connected on p:" + p.id());

        assertValidPipe(p);

        // IMPORTANT: keep the order of operations from below to the dequeue

        int prevbest = bestConnectedPref_();

        pipeConnected_(p);

        OutArg<Prio> op = new OutArg<Prio>(Prio.LO);
        Out o;

        if (prevbest != worstPossiblePref()) {
            o = _q.tryDequeue(op);
            assert o == null : (_pream + " prev connect but q ne");
            return; // someone else already connected, so we have no work to do
        }

        _connecting = false; // FIXME: can I move this into pipeConnected_?

        int curPipeConnSeqNum = getConnSeqNum(p);
        while ((o = _q.tryDequeue(op)) != null) {
            try {
                DIDPipeCookie cke = o.cke();

                //
                // special case to handle 1st stream chunk
                //
                // it turns out that a stream is started by sending <em>two</em>
                // packets. The first packet is a control packet that begins
                // the stream. The second is the 1st chunk. I had never expected
                // this behaviour. My understanding is that we would only ever
                // have one waiting chunk per stream. While this isn't an issue
                // in the current, fully-event-driven implementation, where
                // all events are processed sequentially, and both packets will
                // be enqueued back to back <em>without</em> an intervening
                // connection event, it could be a problem in some hypothetical
                // future. In that case, you could enqueue the control packet,
                // have multiple connection events occur, overwrite the value in
                // the stream cookie, and end up sending these logical back-to-back
                // packets on two different channels. To prevent against that,
                // I'm explicitly coding this assert in as a safety net
                //

                if (cke.set_()) {
                    assert cke.p_().rank() == p.rank() && cke.connSeqNum_() == curPipeConnSeqNum :
                        (_pream + " mismatched cke params " +
                         "expect: p:" + p.id() + " csn:" + curPipeConnSeqNum +
                         "actual: p:" + cke.p_().rank() + " csn:" + cke.connSeqNum_());
                } else {
                    cke.set_(p, curPipeConnSeqNum);
                }

                p.send_(_did, o.wtr(), op.get(), o.bss(), cke);
            } catch (Exception e) {
                o.wtr().error(e);
            }
        }
    }

    void peerDisconnected_(IPipe p)
    {
        l.info(_pream + " disconnected on p:" + p.id());

        pipeDisconnected_(p);
    }

    /**
     * Send a packet to the peer. This method implements the actual channel
     * switching algorithm.
     *
     * @param wtr
     * @param pri
     * @param bss
     * @param origcke
     * @return
     * @throws Exception
     */
    Object send_(IResultWaiter wtr, Prio pri, byte[][] bss, Object origcke)
        throws Exception
    {
        if (wtr == null) wtr = NOOP_WAITER;
        DIDPipeCookie cke = (origcke != null ? (DIDPipeCookie) origcke : new DIDPipeCookie(_did));

        connectToBetters_();

        if (origcke != null && cke.set_()) {
            assert cke.p_().rank() != worstPossiblePref() :
                (_pream + " cke set_ incorrectly to worst pipe");

            IPipe oldpipe = cke.p_();
            int curPipeConnSeqNum = getConnSeqNum(oldpipe);
            int oldPipeConnSeqNum = cke.connSeqNum_();

            if ((curPipeConnSeqNum != oldPipeConnSeqNum) ||
                (bestConnectedPref_() == worstPossiblePref())) {
                wtr.error(new IOException("invalid pipe"));
            } else {
                oldpipe.send_(_did, wtr, pri, bss, cke);
            }
        } else if (bestConnectedPref_() != worstPossiblePref()) {
            IPipe best = bestConnectedPipe_();
            int curPipeConnSeqNum = getConnSeqNum(best);
            cke.set_(best, curPipeConnSeqNum);
            best.send_(_did, wtr, pri, bss, cke);
        } else if (bestConnectedPref_() == worstPossiblePref()) {
            _q.enqueueThrows(new Out(wtr, bss, cke), pri);
        } else {
            assert false : (_pream + " unhandled send_ condition");
        }

        assert cke != null : ("dncs: send_ should never return null cke");
        return cke;
    }

    /**
     * Connect to better transport channels than the best currently-connected one
     */
    private void connectToBetters_()
    {
        if (_connecting) {
            l.debug(_pream + " connect attempt ongoing");
            return;
        }

        Collection<DIDPipeConnectionCounter> better = _available.headMap(bestConnectedPref_()).values();
        if (better.isEmpty()) {
            l.debug(_pream + " no betters exist" +
                " bestcon:" + bestConnectedPref_() + " bestavl:" + bestAvailablePref_());
            return;
        }

        l.debug(_pream + " begin connect betters:" + better.size());

        for(DIDPipeConnectionCounter tcc : better) {
            l.info(_pream + " connect to p:" + tcc.p().id());
            tcc.p().connect_(_did);
        }

        _connecting = true;
        schedConnectTimeout_(++_connAttemptSeqNum);
    }

    /**
     * Schedule a timeout for a connection attempt. This method also describes
     * how connection timeouts are handled.
     *
     * @param curConnAttempSeqNum sequence number of this connection attempt
     */
    private void schedConnectTimeout_(final int curConnAttempSeqNum)
    {
        _sched.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                l.warn(_pream + " connect timeout");

                DIDPipeRouter<T> self = DIDPipeRouter.this;
                self._connecting = false;

                if (self.bestConnectedPref_() < worstPossiblePref()) {
                    l.info(_pream + " connected pipe exists");
                    return;
                }

                if (self._connAttemptSeqNum > curConnAttempSeqNum) {
                    l.info(_pream + " new connect attempt started" +
                        " old:" + curConnAttempSeqNum + " new:" + self._connAttemptSeqNum);
                    return;
                }

                l.warn(_pream + " no connected pipes - drop pkts");

                Out o;
                OutArg<Prio> op = new OutArg<Prio>(Prio.LO);
                while ((o = _q.tryDequeue(op)) != null) {
                    o.wtr().error(new IOException("no transport available"));
                    l.warn(_pream + " drop pkt");
                }
            }
        }, CONNECT_TIMEOUT);
    }

    /**
     *
     * @param p
     */
    private void pipeConnected_(IPipe p)
    {
        assertValidPipe(p);

        boolean added = _connected.add(p);
        assert added : (_pream + " fail add p:" + p.id());

        DIDPipeConnectionCounter pcc = _available.get(p.rank());
        assert pcc != null : (_pream + " no pcc p:" + p.id());

        pcc.increment_(); // don't have to put it back into the map
    }

    /**
     *
     * @param p
     */
    private void pipeDisconnected_(IPipe p)
    {
        assertValidPipe(p);

        // IMPORTANT: do not assert that the pcc was removed. Underlying layers
        // are lax about signalling us. They will signal us on a disconnection even
        // if there was no valid connection

        // FIXME: make lower layers more strict in how they signal us

        boolean removed = _connected.remove(p);
        if (!removed) l.warn(_pream + " no connection p:" + p.id());
    }

    /**
     *
     * @param p
     * @return
     */
    private int getConnSeqNum(IPipe p)
    {
        DIDPipeConnectionCounter pcc = _available.get(p.rank());
        assert pcc != null : (_pream + " invalid p:" + p.id());

        return pcc.connSeqNum_();
    }

    /**
     * @return the preference of the best connected {@link IPipe}
     */
    private int bestConnectedPref_()
    {
        return _connected.first().rank();
    }

    /**
     * @return the best connected {@link IPipe}
     */
    private IPipe bestConnectedPipe_()
    {
        return _connected.first();
    }

    /**
     * @return the preference of the best <em>available (i.e. configured)</em> {@link IPipe}
     */
    private int bestAvailablePref_()
    {
        return _available.firstKey();
    }

    /**
     * @return the preference of the <code>ERROR_PIPE</code>
     */
    private static int worstPossiblePref()
    {
        return ERROR_PIPE.rank();
    }

    /**
     * Asserts that the {@link IPipe} passed in as a parameter is not an instance
     * of <code>ERROR_PIPE</code>
     *
     * @param p {@link IPipe} to check
     */
    private void assertValidPipe(IPipe p)
    {
        assert p.rank() != worstPossiblePref() : (_pream + " invalid p:" + p.id());
    }

    /**
     * Construct a sorted map of {@link DIDPipeConnectionCounter} sorted by
     * {@link IPipe} preferences. In this map, the entry with the lowest number
     * (i.e. the highest preference) is the fastest to access.
     *
     * @param pipes
     * @return
     */
    private SortedMap<Integer, DIDPipeConnectionCounter> makedpccs(Set<T> pipes)
    {
        SortedMap<Integer, DIDPipeConnectionCounter> sorted = new TreeMap<Integer, DIDPipeConnectionCounter>();
        for (IPipe p : pipes) {
            DIDPipeConnectionCounter pcc = new DIDPipeConnectionCounter(p);
            DIDPipeConnectionCounter old = sorted.put(p.rank(), pcc);

            assert old == null: (_pream + " pcc already exists p:" + p.id());
        }

        return sorted;
    }

    @Override
    public String toString()
    {
        return _pream +
            " avl:" + _available.size() + " con:" + _connected.size() +
            " conattsseqnum:" + _connAttemptSeqNum +
            " bestavl:" + bestAvailablePref_() + " bestcon:" + bestConnectedPref_() +
            " bestconid:" + bestConnectedPipe_().id();
    }

    //
    // types
    //

    /**
     * Used to represent each outgoing packet that is queued until an underlying
     * pipe connects or, a connection timeout for the 'set' occurs.
     */
    private class Out
    {
        /**
         * Constructor
         * <br/>
         * Expects <em>all</em> parameters to be valid and not-null
         *
         * @param wtr {@link IResultWaiter} for this <code>send_</code>
         * @param bss bytes to be sent to the peer in this request
         * @param cke a valid {@link DIDPipeCookie} with mutable fields <em>unset</em>
         */
        Out(IResultWaiter wtr, byte[][] bss, DIDPipeCookie cke)
        {
            assert wtr != null && bss != null && bss.length != 0 && cke != null: ("invalid args");

            _wtr = wtr;
            _bss = bss;
            _cke = cke;
        }

        /**
         * @return a valid {@link IResultWaiter} - either real, or a <code>NOOP_WAITER</code>
         */
        IResultWaiter wtr()
        {
            return _wtr;
        }

        /**
         * @return request to be sent to the peer
         */
        byte[][] bss()
        {
            return _bss;
        }

        /**
         * @return a valid {@link DIDPipeCookie} with mutable fields <em>unset</em>
         */
        DIDPipeCookie cke()
        {
            return _cke;
        }

        private final IResultWaiter _wtr;
        private final byte[][] _bss;
        private final DIDPipeCookie _cke;
    }

    //
    // members
    //

    private final DID _did;
    private final String _pream;
    private final IScheduler _sched;
    private final SortedMap<Integer, DIDPipeConnectionCounter> _available; // rank -> DIDPipeConnectionCounter
    private final SortedSet<IPipe> _connected = new TreeSet<IPipe>(new DefaultComparator());

    private boolean _connecting = false;
    private int _connAttemptSeqNum = INITIAL_CONNECT_ATTEMPT_SEQ_NUM;
    private BlockingPrioQueue<Out> _q = new BlockingPrioQueue<Out>(QUEUE_LENGTH_DEFAULT);

    private static final int INITIAL_CONNECT_ATTEMPT_SEQ_NUM = 0;

    private static final Logger l = Loggers.getLogger(DIDPipeRouter.class);
}
