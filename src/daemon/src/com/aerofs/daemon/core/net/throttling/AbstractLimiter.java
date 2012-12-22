package com.aerofs.daemon.core.net.throttling;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.PrioQueue;
import com.aerofs.daemon.lib.Scheduler;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.proto.Limit;
import org.apache.log4j.Logger;
import javax.annotation.Nonnull;

// TODO: Set the current time at the start of the event response interval
// TODO: Have to have a way to set the token flag on outgoing messages
abstract class AbstractLimiter implements ILimiter
{
    private static final int _TOSTRING_INITIAL_CAPACITY = 64;
    private static final int _PRINTSTAT_INITIAL_CAPACITY = 128;

    protected static final int _PBLIMIT_APPROX_HEADER_LEN = 2;

    protected class NextTimeoutInfo
    {
        NextTimeoutInfo(long timeout, Prio p)
        {
            _timeout = timeout;
            _p = p;
        }

        @Override
        public String toString()
        {
            return "tim:" + _timeout + " pr:" + _p;
        }

        long _timeout;
        Prio _p;
    }

    /**
     * maximum number of packets that can be queued up in the traffic-shaping
     * queue
     */
    protected static final int _MAX_SHAPING_Q_BACKLOG = 10; // packets

    protected static final long _NO_PENDING_FILL_RATE = -1L;

    protected static final long _NO_TIME = -1L;

    protected static final int _MS_PER_SEC = 1000;

    // BUG BUG: Extracting this class resulted in this line being kept:
    // private Logger l = Util.l(GlobalChoker.DeviceProperties.class);
    @Nonnull
    protected Logger l = null;

    /**
     * absolute minimum capacity of the token bucket
     */
    protected final long _minBucket;

    /**
     * capacity of the token bucket. As <code>_fillRate</code> is increased
     * beyond <code>_minBucket</code>, <code>_bucket</code> increases in
     * lock-step. If, however, <code>fillRate</code> drops below
     * <code>_minBucket</code>, then <code>_bucket</code> is held at
     * <code>_minBucket</code>
     */
    protected long _bucket;

    /**
     * number of tokens currently in the token bucket
     */
    protected long _tokens;

    /**
     * device-specific rate at which the bucket is filled given as KB/sec
     */
    protected long _fillRate;

    /**
     * changes in fill-rate are not reflected immediately. If we can't respond
     * to it immediately, the value we should use when we <i>do</i> change is
     * stored in _pendingFillRate;
     */
    protected long _pendingFillRate;

    /**
     * time at which the last packet successfully passed through the device's
     * token bucket
     */
    protected long _lastConfirmTime;

    /**
     * time at which the _nextTimeout will occur; null if there is no pending
     * timeout for this device
     */
    protected NextTimeoutInfo _nextTimeout;

    /**
     * holds all the pending (to be confirmed) outgoing packets for this device
     */
    protected PrioQueue<Outgoing> _q;

    private final Scheduler _sched;

    /**
     * Construct a GlobalChoker with a single priority queue
     *
     * @param minBucket absolute minimum depth of the token bucket in bytes.
     * Represents the largest packet that can be sent via the system
     * @param bucket depth of the _bucket in bytes
     * @param fillRate fill-rate in kbps for this bucket
     * @param pendingSize size of the pending-packet queue
     */
    AbstractLimiter(Scheduler sched, Logger logger, long minBucket, long bucket,
            long fillRate, int pendingSize)
    {
        assert bucket >= minBucket;

        _sched = sched;
        l = logger;
        _minBucket = minBucket;
        _bucket = bucket;
        _tokens = 0;
        _fillRate = fillRate;
        _pendingFillRate = _NO_PENDING_FILL_RATE;
        _lastConfirmTime = 0;
        _nextTimeout = null;
        _q = new PrioQueue<Outgoing>(pendingSize);
    }

    public void respondToTimeIn_()
            throws Exception
    {
        if (l.isDebugEnabled()) {
            l.trace(name() + ": beg tihd:" + System.currentTimeMillis());
            l.trace(printstat_());
        }

        Exception procEx = null;
        try {
            boolean continueDrain = true;
            while (continueDrain) {
                if (hasPending_()) {
                    l.trace(name() + ": has pnd");

                    Outgoing op = peekPending_(); // nt
                    Prio p = peekPendingPrio_(); // nt

                    printOutgoingParams_(op, p);

                    if (hasTokens_(op)) { // nt
                        l.trace(name() + ": has tok");

                        Outgoing o = getPending_(); // nt
                        assert o == op;
                        //
                        // IMPORTANT: after this point o removed from queue
                        //
                        confirm_(o); // nt
                        indicateTokensNeeded_(o); // nt

                        try {
                            processConfirmedOutgoing_(o, p); // throw
                        } catch (Exception e) {
                            // FIXME: this seems dangerous
                            procEx = new ExOutgoingProcessingError(o, e);
                        }
                    } else {
                        l.trace(name() + ": no tok");
                        continueDrain = false;
                    }
                } else {
                    l.trace(name() + ": no pnd");
                    continueDrain = false;
                }
            }
        } finally {
            scheduleNextTimeout_();
            syncFillRate_();
            if (procEx != null) throw procEx;
        }

        l.trace(name() + ": fin tihd");
    }

    /**
     * Processes a <code>Outgoing</code> that was confirmed by a
     * <code>Choker</code> higher in the choking hierarchy. Can also be used if
     * this is the first time a <code>Outgoing</code> is entering the Choking
     * hierarchy
     *
     * @throws Exception <b>IMPORTANT:</b> No rollback will occur when an
     * exception is thrown. If <code>o</code> used up tokens they will not be
     * returned to the token pool.
     * <p/>
     * <b>IMPORTANT:</b> Do not use if the <code>o</code> is inside the pending
     * queue and is being checked within an event.
     */
    @Override
    public void processOutgoing_(Outgoing o, Prio p)
            throws Exception
    {
        if (l.isDebugEnabled()) {
            l.trace(name() + ": beg po");
            l.trace(printstat_());
        }

        printOutgoingParams_(o, p);

        if (!hasPending_()) { // nt
            l.trace(name() + ": no pnd");

            if (hasTokens_(o)) { // nt
                l.trace(name() + ": has tok");
                confirm_(o); // nt
                processConfirmedOutgoing_(o, p); // throw
            } else {
                l.trace(name() + ": no tok");
                addPending_(o, p); // throw
                scheduleNextTimeout_(); // nt
            }
        } else {
            l.trace(name() + ": has pnd");

            if (peekPendingPrio_() == Prio.higher(p, peekPendingPrio_())) {
                l.trace(name() + ": lo p");
                addPending_(o, p); // throw
            } else {
                l.trace(name() + ": hi p");
                if (hasTokens_(o)) { // nt
                    l.trace(name() + ": has tok");
                    confirm_(o); // nt
                    scheduleNextTimeout_(); // nt
                    indicateTokensNeeded_(o); // nt
                    processConfirmedOutgoing_(o, p); // throw
                } else {
                    // FIXME: this happens regardless of whether you have less size than the current LO-prio waiter...
                    l.trace(name() + ": no tok");
                    addPending_(o, p); // throw
                    scheduleNextTimeout_(); // nt
                }
            }
        }

        l.trace(name() + ": fin po");
    }

    // FIXME: I'm pretty sure this is a bad idea

    /**
     * Triggered when there are pending packets in the limiter queue
     */
    protected void indicateTokensNeeded_(Outgoing o)
    {
        // noop
    }

    protected String toString(long now)
    {
        return new StringBuilder(_TOSTRING_INITIAL_CAPACITY)
                .append("bkt:")
                .append(_bucket)
                .append(" tok:")
                .append(now == _NO_TIME ? _tokens : tokensAvailable_(now))
                .append(" fr:")
                .append(_fillRate)
                .append(" pdg:")
                .append(_pendingFillRate)
                .append(" qsz:[")
                .append(_q)
                .append("] lct:")
                .append(_lastConfirmTime)
                .append(" nt:[")
                .append(_nextTimeout != null ? _nextTimeout.toString() : "NONE")
                .append("]")
                .toString();
    }

    protected String printstat_()
    {
        long now = System.currentTimeMillis();
        return new StringBuilder(_PRINTSTAT_INITIAL_CAPACITY)
                .append(name())
                .append(": ")
                .append(toString(now))
                .append(" now:")
                .append(now)
                .toString();
    }

    protected void printOutgoingParams_(Outgoing o, Prio p)
    {
        l.trace(name() + ": len:" + o.getLength() + " pr:" + p);
    }

    /**
     * Modifies <code>_bucket</code>, <code>_tokens</code> and
     * <code>_fillRate</code> based on the input <code>fillRate</code>
     *
     * @param fillRate new fill-rate of the token bucket
     */
    protected void setBwParams_(long fillRate)
    {
        long oldBucket = _bucket, oldFillRate = _fillRate;

        _bucket = ((fillRate < _minBucket) ? _minBucket : fillRate);
        _fillRate = fillRate;
        _tokens = (_tokens > _bucket ? _bucket : _tokens);

        l.debug(name() + ": upd bk:" + oldBucket + "->" + _bucket +
                " fr:" + oldFillRate + "->" + _fillRate);
    }

    protected long tokensAvailable_(long now)
    {
        long tokAvail = _tokens + (long) (((now - _lastConfirmTime) / (double) _MS_PER_SEC) * _fillRate);
        return ((tokAvail > _bucket || tokAvail < 0) ? _bucket : tokAvail);
    }

    protected boolean hasTokens_(Outgoing o)
    {
        assert o.getLength() <= _bucket : (o.getLength() + " > " + _bucket);
        return tokensAvailable_(System.currentTimeMillis()) >= o.getLength();
    }

    protected boolean hasPending_()
    {
        return !_q.isEmpty_();
    }

    protected void addPending_(Outgoing o, Prio p)
            throws ExNoResource
    {
        if (_q.isFull_()) throw new ExNoResource(name() + ": q full");

        l.trace(name() + ": add pnd");
        _q.enqueue_(o, p);
    }

    protected Outgoing getPending_()
    {
        assert !_q.isEmpty_();
        return _q.dequeue_();
    }

    protected Outgoing peekPending_()
    {
        assert !_q.isEmpty_();
        return _q.peek_(null);
    }

    protected Prio peekPendingPrio_()
    {
        assert !_q.isEmpty_();

        OutArg<Prio> peekPrio = new OutArg<Prio>(Prio.LO);
        _q.peek_(peekPrio);
        return peekPrio.get();
    }

    protected void confirm_(Outgoing o)
    {
        // FIXME: Don't do this twice!
        long currTime = System.currentTimeMillis();

        long tokAvail = tokensAvailable_(currTime);
        assert tokAvail >= o.getLength();
        tokAvail -= o.getLength();

        _tokens = tokAvail;
        _lastConfirmTime = currTime;
    }

    protected void syncFillRate_()
    {
        if (_pendingFillRate != _NO_PENDING_FILL_RATE) {
            setBwParams_(_pendingFillRate);
            _pendingFillRate = _NO_PENDING_FILL_RATE;
        }
    }

    protected void scheduleNextTimeout_()
    {
        NextTimeoutInfo nt = null;
        if (!_q.isEmpty_()) {
            l.trace(name() + ": pnd q ne");

            OutArg<Prio> peekPrio = new OutArg<Prio>(Prio.LO);
            Outgoing o = _q.peek_(peekPrio);

            long timeDiff =
                    (long) Math.ceil(((o.getLength() - _tokens) / (double) _fillRate) * _MS_PER_SEC);

            l.trace(name() + ": wait:" + timeDiff);

            nt = new NextTimeoutInfo(
                    System.currentTimeMillis() + timeDiff, peekPrio.get());

            AbstractEBSelfHandling ce = new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    try {
                        respondToTimeIn_();
                    } catch (ExOutgoingProcessingError e) {
                        l.warn(name() + ": ex: " +
                                e.getEx().getClass().getSimpleName());
                        e.getCk().finishProcessing(e.getEx());
                    } catch (Exception e) {
                        l.error(name() + ": unexpected ex");
                        assert false;
                    }
                }
            };
            _sched.schedule(ce, timeDiff);
        } else {
            l.trace(name() + ": pnd q e");
        }

        _nextTimeout = nt;
    }

    @Override
    public void processControlLimit_(DID d, Limit.PBLimit pbl)
    {
        if (_nextTimeout == null) {
            assert _pendingFillRate == _NO_PENDING_FILL_RATE;
            setBwParams_(pbl.getBandwidth());
        } else {
            _pendingFillRate = pbl.getBandwidth();
        }
    }

    //
    // Object
    //

    @Override
    public String toString()
    {
        return toString(_NO_TIME);
    }
}
