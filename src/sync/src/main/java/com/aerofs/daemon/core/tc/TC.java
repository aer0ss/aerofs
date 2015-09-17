package com.aerofs.daemon.core.tc;

import java.io.PrintStream;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.Dumpables;
import com.aerofs.daemon.core.tc.TokenManager.ITokenUseListener;
import com.aerofs.daemon.lib.db.trans.TransBoundaryChecker;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.StrictLock;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.lib.db.DBIteratorMonitor;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Thread Control */
public class TC implements IDumpStatMisc, ITokenUseListener
{
    private static final Logger l = Loggers.getLogger(TC.class);

    static {
        //noinspection ConstantConditions
        Preconditions.checkState(DaemonParam.TC_RECLAIM_LO_WATERMARK >= 1);
    }

    static final long FOREVER = Long.MAX_VALUE;

    // thread control block
    public class TCB implements AutoCloseable
    {
        private final Condition _cv = _l.newCondition();

        private boolean _running = true;
        private Prio _prio;

        // valid only if !_running
        private Token _tk;

        private @Nullable Throwable _abortCause;

        // for dumping only
        private final Thread _thd = Thread.currentThread();
        private String _pauseReason;

        /**
         * @return false if the thread is already resumed or aborted
         */
        public boolean resume_()
        {
            l.trace("resume " + _thd.getName());
            return unblockWithThrowable_(null);
        }

        // N.B. since the same tcb is reused for each thread, the user must
        // guarantee that events irrelevant to a thread's pausing don't resume
        // the thread. This may happen when, say, a thread's pausing times out
        // before an event arrives, and the thread pauses again, after which
        // that event arrives. To prevent this, all ``pending'' events must be
        // canceled out whenever a thread wakes up.
        //
        /**
         * @return false if the thread is already resumed or aborted
         */
        private boolean unblockWithThrowable_(@Nullable Throwable cause)
        {
            Preconditions.checkState(_l.isHeldByCurrentThread());

            if (_running) {
                l.trace("already running");
                return false;
            } else {
                _running = true;
                _abortCause = cause;
                _cv.signal();
                return true;
            }
        }

        /**
         * @return false if the thread is already resumed or aborted
         */
        public boolean abort_(@Nonnull Throwable cause)
        {
            l.debug("abort {}: {}", _thd.getName(), cause);

            return unblockWithThrowable_(Preconditions.checkNotNull(cause));
        }

        public void pseudoResumed_() throws ExAborted
        {
            _l.lock();
            postPause_(this);
        }

        private boolean holdsCoreLock_() { return _l.isHeldByCurrentThread(); }

        /**
         * @return false if timeout occurs
         */
        boolean park_(Cat cat, long timeout)
        {
            l.trace("pause {}@{} {}: {}", _prio, cat,
                    (timeout == FOREVER ? "forever" : timeout), _pauseReason);

            boolean ret = true;
            while (!_running && ret) {
                try {
                    ret = _cv.await(timeout, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw SystemUtil.fatal(e);
                }
            }

            return ret;
        }

        void pseudoPause_()
        {
            _l.unlock();
        }

        @Override
        public String toString()
        {
            return _thd.getName() + (_running ? " running" : " " + _pauseReason);
        }

        @Override
        public final void close() throws ExAborted
        {
            pseudoResumed_();
        }
    }

    private final static ThreadLocal<TCB> tl_tcb = new ThreadLocal<>();

    private final static String THREAD_NAME_PREFIX = "c";

    private int _total;

    private int _pendingQuits;

    // for debugging/statistics only
    private final Set<TCB> _paused = Sets.newHashSet();

    private final Queue<String> _threadNamePool = Lists.newLinkedList();
    private final CoreQueue _q;
    private final StrictLock _l;
    private final CoreEventDispatcher _disp;
    private final CoreScheduler _sched;
    private final TransBoundaryChecker _tm;

    private final Condition _resumed;
    private final ThreadGroup _threadGroup = new ThreadGroup("core");

    @Inject
    public TC(CoreQueue q, CoreEventDispatcher disp, CoreScheduler sched,
            TokenManager tokenManager, TransBoundaryChecker tm)
    {
        _sched = sched;
        _disp = disp;
        _q = q;
        _tm = tm;
        _l = new StrictLock();
        _resumed = _l.newCondition();

        // break circular dependency w/ listener pattern
        tokenManager.setListener_(this);

        Dumpables.add("tc", this);
    }

    private volatile boolean _suspended = false;

    /**
     * Suspend event processing
     * NB: This will not interrupt ongoing events, simply delay the processing of enqueued and
     * future events until {@link #resume_} is called.
     */
    public void suspend_()
    {
        _suspended = true;
    }

    /**
     * Resume event processing after a call to {@link #suspend_}
     */
    public void resume_()
    {
        _suspended = false;
        _resumed.signalAll();
    }

    private void waitResumed() throws InterruptedException
    {
        while (_suspended) _resumed.await();
    }

    private void newThread_()
    {
        int n = ++_total;
        final String name = _threadNamePool.isEmpty() ? THREAD_NAME_PREFIX + n : _threadNamePool.remove();

        l.trace("new: {} ({})", name, _total);

        Thread thd = new Thread(_threadGroup, new Runnable() {
            @Override
            public void run()
            {
                tl_tcb.set(new TCB());
                IEvent ev;
                OutArg<Prio> outPrio = new OutArg<>();

                try {
                    do {
                        ev = dequeue(outPrio);
                    } while (process(ev, outPrio.get()));
                } catch (Throwable e) {
                    // fail fast
                    throw SystemUtil.fatal(e);
                }
            }

            private IEvent dequeue(OutArg<Prio> outPrio) throws InterruptedException
            {
                IEvent ev;

                l.trace("waiting");
                while (true) {
                    // ensure we're cleared to process events
                    _l.lock();

                    // try to dequeue an event
                    _q.getLock().lock();
                    ev = _q.tryDequeue_(outPrio);

                    // we did it!
                    if (ev != null) {
                        // allow other threads to enqueue events
                        _q.getLock().unlock();
                        return ev;
                    }

                    // no events available
                    // release core lock to allow paused threads to resume
                    _l.unlock();

                    // wait for core queue to refill
                    _q.await_();

                    // must release queue lock before re-acquiring core lock
                    _q.getLock().unlock();
                }
            }

            private boolean process(IEvent ev, Prio prio) throws InterruptedException
            {
                try {
                    waitResumed();
                    l.trace("processing");

                    if (ev == EV_QUIT) {
                        _pendingQuits--;
                        testReclamation_();
                        if (_total - _paused.size() >
                                DaemonParam.TC_RECLAIM_LO_WATERMARK) {
                            l.trace("{} reclaimed", name);
                            _threadNamePool.offer(name);
                            _total--;
                            return false;
                        }
                    }

                    setPrio(prio);

                    _disp.dispatch_(ev, prio);

                    _tm.assertNoOngoingTransaction_();
                } finally {
                    _l.unlock();
                }
                return true;
            }
        }, name);

        thd.setDaemon(true);
        thd.start();
    }

    private static final IEvent EV_QUIT = new AbstractEBSelfHandling() {
        @Override
        public void handle_()
        {
        }
    };

    public void start_()
    {
        newThread_();
    }

    public static TCB tcb()
    {
        return tl_tcb.get();
    }

    /**
     * @return the priority of the current core thread
     */
    public static Prio currentThreadPrio()
    {
        TCB tcb = tcb();
        return tcb != null && tcb._running ? tcb._prio : null;
    }

    /**
     * set the priority of the current core thread
     */
    public Prio setPrio(Prio prio)
    {
        Prio old = tcb()._prio;
        tcb()._prio = prio;
        return old;
    }

    // for debugging only
    public static boolean _coreLockChecks = true;
    public static void assertHoldsCoreLock_() {
        if (!_coreLockChecks) return;
        TCB tcb = tcb();
        if (tcb == null || !tcb.holdsCoreLock_()) throw new AssertionError();
    }

    @Override
    public void prePause_(TCB tcb, Token tk, String reason) throws ExAborted
    {
        l.trace("p-pause {} {}", tcb._prio, reason);
        Preconditions.checkState(_l.isHeldByCurrentThread());
        Preconditions.checkState(_paused.size() < _total);
        Preconditions.checkArgument(!reason.isEmpty());

        // there mustn't be active transactions or iterators before going to sleep
        _tm.assertNoOngoingTransaction_();
        DBIteratorMonitor.assertNoActiveIterators_();

        Preconditions.checkState(!_l.hasWaiters(tcb._cv));
        Preconditions.checkState(tcb._running);

        if (tk.isReclaimed_()) throw new ExAborted("token reclaimed");

        tk.addTCB_(tcb);
        tcb._tk = tk;

        // the method must not throw after this point
        tcb._running = false;
        tcb._pauseReason = reason;
        tcb._abortCause = null;

        Util.verify(_paused.add(tcb));
        if (_total - _paused.size() < DaemonParam.TC_RECLAIM_LO_WATERMARK) {
            newThread_();
        }
    }

    @Override
    public void postPause_(TCB tcb) throws ExAborted
    {
        Preconditions.checkArgument(tcb == tcb());

        Util.verify(_paused.remove(tcb));

        testReclamation_();

        tcb._tk.removeTCB_(tcb);
        tcb._tk = null;    // not necessary. for debugging only

        // there mustn't be active transactions or iterators when coming back from sleep
        _tm.assertNoOngoingTransaction_();
        DBIteratorMonitor.assertNoActiveIterators_();

        if (!tcb._running) {
            tcb._running = true;
        } else if (tcb._abortCause != null) {
            if (l.isDebugEnabled()) l.debug("aborted due to " + tcb._abortCause);
            throw new ExAborted(tcb._abortCause);
        }
    }

    private void testReclamation_()
    {
        Preconditions.checkState(_pendingQuits >= 0);
        if (_pendingQuits == 0
                && _total - _paused.size() >= DaemonParam.TC_RECLAIM_HI_WATERMARK) {
            _pendingQuits = _total - _paused.size()
                    - DaemonParam.TC_RECLAIM_LO_WATERMARK;
            l.trace("time to reclaim. target: {}", _pendingQuits);
            for (int i = 0; i < _pendingQuits; i++) {
                _sched.schedule(EV_QUIT, DaemonParam.TC_RECLAIM_DELAY);
            }
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        ps.println(indent + _paused.size() + " paused, " + _total + " total");

        for (TCB tcb : _paused) {
            ps.println(indent + tcb + '\t' + tcb._prio + ' ' + tcb._pauseReason);
        }
    }
}
