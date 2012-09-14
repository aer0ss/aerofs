package com.aerofs.daemon.core.tc;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.IDumpStatMisc;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.db.DBIteratorMonitor;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.ex.ExTimeout;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.StrictLock;
import com.aerofs.lib.Util;
import com.aerofs.lib.spsv.SVClient;

import javax.annotation.Nullable;

/** Thread Control */
public class TC implements IDumpStatMisc
{
    private static final Logger l = Util.l(TC.class);

    static final long FOREVER = Long.MAX_VALUE;

    static {
        assert DaemonParam.TC_RECLAIM_LO_WATERMARK >= 1;
    }

    // thread control block
    public class TCB {
        private final Condition _cv = _l.newProfiledCondition();

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
            return abort_(null);
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
        public boolean abort_(@Nullable Throwable cause)
        {
            l.info("abort " + _thd.getName() + ": " + cause);

            assert _l.isHeldByCurrentThread();

            if (_running) {
                l.info("already running");
                return false;
            } else {
                _running = true;
                _abortCause = cause;
                _cv.signal();
                return true;
            }
        }

        public void pseudoResumed_() throws ExAborted
        {
            _l.lock();
            postPause_(this);
        }

        @Override
        public String toString()
        {
            return _thd.getName() + (_running ? " running" : " " + _pauseReason);
        }
    }

    private final static ThreadLocal<TCB> tl_tcb = new ThreadLocal<TCB>();

    private final static String THREAD_NAME_PREFIX = "c";

    private int _total;

    private int _pendingQuits;

    private boolean _shutdown;

    // for debugging/statistics only
    private final Set<TCB> _paused = new HashSet<TCB>();

    private final LinkedList<String> _threadNamePool = new LinkedList<String>();
    private CoreQueue _q;
    private StrictLock _l;
    private CoreEventDispatcher _disp;
    private TransManager _tm;
    private CoreScheduler _sched;
    private TokenManager _tokenManager;

    private final ThreadGroup _threadGroup = new ThreadGroup("core");


    /**
     * N.B. runnable.run() must return occasionally (we call it recursively in
     * an infinite loop), and must use pause_() before any blocking operations.
     * The lock l is locked while runnable.run() is executing.
     */
    @Inject
    public void inject_(CoreScheduler sched, TransManager tm,
            CoreEventDispatcher disp, CoreQueue q, TokenManager tokenManager)
    {
        _sched = sched;
        _tm = tm;
        _disp = disp;
        _q = q;
        _l = q.getLock();
        _tokenManager = tokenManager;
    }

    public StrictLock getLock()
    {
        return _l;
    }

    private void newThread_()
    {
        _total++;
        final String name = _threadNamePool.isEmpty() ? THREAD_NAME_PREFIX
                + _total : _threadNamePool.removeFirst();

        l.info("new: " + name + " (" + _total + ")");

        Thread thd = new Thread(_threadGroup, new Runnable() {
            @Override
            public void run()
            {
                tl_tcb.set(new TCB());

                OutArg<Prio> outPrio = new OutArg<Prio>();

                _l.lock();
                try {
                    while (true) {
                        try {
                            IEvent ev = _q.dequeue_(outPrio);
                            if (_shutdown) {
                                continueShutdown_();
                            }
                            if (ev == EV_QUIT) {
                                _pendingQuits--;
                                testReclamation_();
                                if (_shutdown || _total - _paused.size() >
                                        DaemonParam.TC_RECLAIM_LO_WATERMARK) break;
                            }

                            setPrio(outPrio.get());

                            _disp.dispatch_(ev, outPrio.get());

                            assert !_tm.hasOngoingTransaction_() : ev.getClass();
                        } catch (Throwable e) {
                            try {
                                SVClient.logSendDefectAsync(true, "caught by TC @ " +
                                        Thread.currentThread().getName(), e);
                            } finally {
                                // better to fail fast
                                Util.fatal(e);
                            }
                        }
                    }

                    l.info(name + " reclaimed");
                    _threadNamePool.addLast(name);
                    _total--;

                } finally {
                    _l.unlock();
                }
            }
        }, name);

        if (Cfg.isSP()) {
            // set core thread priority higher than other threads for
            // shorter response time on core activities
            //
            // ref1 http://www.javamex.com/tutorials/threads/priority_what.shtml
            // ref2 http://stackoverflow.com/questions/297804/thread-api-priority-translation-to-os-thread-priority
            // ref3 http://www.akshaal.info/2008/04/javas-thread-priorities-in-linux.html
            // ref4 http://tech.stolsvik.com/2010/01/linux-java-thread-priorities-workaround.html
            //
            // -XX:ThreadPriorityPolicy=666 must be set on Linux. it's a hack.
            // it also needs "aerofs - nice -5" in /etc/security/limits.conf.
            // see ref3 & 4
            //
            thd.setPriority(Thread.NORM_PRIORITY + (Thread.MAX_PRIORITY -
                    Thread.NORM_PRIORITY) / 2);
        }

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


    private void continueShutdown_()
    {
        while (_pendingQuits < _total) {
            if (!_q.enqueue_(EV_QUIT, Prio.LO)) break;
            ++_pendingQuits;
        }
    }

    public void shutdown_()
    {
        _shutdown = true;
        continueShutdown_();
    }

    public static TCB tcb()
    {
        return tl_tcb.get();
    }

    public boolean isCoreThread()
    {
        return tl_tcb.get() != null;
    }

    /**
     * @return the priority of the current core thread
     */
    public Prio prio()
    {
        return tcb()._prio;
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

    public Token acquireThrows_(Cat cat, String reason) throws ExNoResource
    {
        return _tokenManager.acquireThrows_(cat, reason);
    }

    public Token acquire_(Cat cat, String reason)
    {
        return _tokenManager.acquire_(cat, reason);
    }


    private void prePause_(TCB tcb, Token tk, String reason) throws ExAborted
    {
        assert _l.isHeldByCurrentThread();
        assert _paused.size() < _total;
        assert reason != null && !reason.isEmpty();

        // there mustn't be active transactions or iterators before going to
        // sleep
        assert !_tm.hasOngoingTransaction_();
        DBIteratorMonitor.assertNoActiveIterators_();

        assert !_l.hasWaiters(tcb._cv);
        assert tcb._running;

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

    // return false if timeout occurs
    private boolean park_(TCB tcb, Cat cat, long timeout)
    {
        l.info("pause " + tcb._prio + '@' + cat + ' '
                + (timeout == FOREVER ? "forever" : timeout) + ": "
                + tcb._pauseReason);

        boolean ret = true;
        while (!tcb._running && ret) {
            try {
                ret = tcb._cv.await(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Util.fatal(e);
            }
        }

        return ret;
    }

    private void testReclamation_()
    {
        assert _pendingQuits >= 0;
        if (_pendingQuits == 0
                && _total - _paused.size() >= DaemonParam.TC_RECLAIM_HI_WATERMARK) {
            _pendingQuits = _total - _paused.size()
                    - DaemonParam.TC_RECLAIM_LO_WATERMARK;
            l.info("time to reclaim. target: " + _pendingQuits);
            for (int i = 0; i < _pendingQuits; i++) {
                _sched.schedule(EV_QUIT, DaemonParam.TC_RECLAIM_DELAY);
            }
        }
    }

    private void postPause_(TCB tcb) throws ExAborted
    {
        assert tcb == tcb();

        Util.verify(_paused.remove(tcb));

        testReclamation_();

        tcb._tk.removeTCB_(tcb);
        tcb._tk = null;    // not necessary. for debugging only

        if (!tcb._running) {
            tcb._running = true;
        } else if (tcb._abortCause != null) {
            l.info("aborted due to " + tcb._abortCause.getClass().getName());
            throw new ExAborted(tcb._abortCause);
        }
    }

    void pauseImpl_(Token tk, String reason) throws ExAborted
    {
        TCB tcb = tcb();
        prePause_(tcb, tk, reason);
        try {
            Util.verify(park_(tcb, tk.getCat(), FOREVER));
        } finally {
            postPause_(tcb);
        }
    }

    void pauseImpl_(Token tk, long timeout, String reason) throws ExAborted, ExTimeout
    {
        TCB tcb = tcb();
        prePause_(tcb, tk, reason);
        boolean timedout;
        try {
            timedout = park_(tcb, tk.getCat(), timeout);
        } finally {
            // abortion checking must precede timeout checking, since in some use
            // cases the application depends on the fact that abort signals can be
            // reliably delivered to the receiving thread (cf. Download)
            postPause_(tcb);
        }

        if (!timedout) {
            l.info("timed out");
            throw new ExTimeout();
        }
    }

    TCB pseudoPauseImpl_(Token tk, String reason) throws ExAborted
    {
        TCB tcb = tcb();
        l.info("p-pause " + tcb._prio + ' ' + reason);
        prePause_(tcb, tk, reason);
        _l.unlock();
        return tcb;
    }

    /**
     * Note that yield may fail silently due to category full
     */
    void yield_(Token tk)
    {
        TCB tcb = tcb();
        Prio old = tcb._prio;
        tcb._prio = Prio.LO;
        try {
            Util.verify(tk.pseudoPause_("yield") == tcb);
            try {
                Thread.yield();
            } finally {
                tcb.pseudoResumed_();
            }
        } catch (Exception e) {
            l.info("cannot yield: " + Util.e(e));
        } finally {
            tcb._prio = old;
        }
    }

    void sleepImpl_(Token tk, long timeout, String reason) throws ExAborted
    {
        TCB tcb = tk.pseudoPause_("sleep: " + reason);
        try {
            Util.sleepUninterruptable(timeout);
        } finally {
            tcb.pseudoResumed_();
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
