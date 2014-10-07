/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.persistency;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.sched.ExponentialRetry;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.google.inject.Inject;

import java.sql.SQLException;

/**
 * Infrastructure for persistent queueing
 *
 * Each instance of this class manages a persistent queue. The implementation of the actual queueing
 * is delegated to the IPersistentQueue interface. This object is in charge of:
 *   - scheduling a scan of the queue every time an object is enqueued
 *   - ensuring that only a single scan of the queue is done at any given time
 *   - dequeuing items from the queue in a core thread, with exponential retry
 *
 * The class is parametrized with two generic types:
 *   - I is the input type, i.e the type of item to be stored in the queue
 *   - O is the output type, i.e the type of items read from the queue
 *
 * In many cases, I and O will be the same type, however in some cases it is interesting to use
 * different types. For instance if the processing involves a remote call, it may make snese to
 * batch multiple items to decrease latency.
 */
public class PersistentQueueDriver<I, O>
{
    private final Factory _f;
    private final ExponentialRetry _er;

    private final IPersistentQueue<I, O> _q;

    private long _scanSeq = 0;
    private boolean _scanInProgress = false;
    private boolean _retryScan = false;

    public static class Factory
    {
        private final TokenManager _tokenManager;
        private final TransManager _tm;
        private final CoreScheduler _sched;

        @Inject
        public Factory(TokenManager tokenManager, TransManager tm, CoreScheduler sched)
        {
            _tokenManager = tokenManager;
            _tm = tm;
            _sched = sched;
        }

        public <I, O> PersistentQueueDriver<I, O> create(IPersistentQueue<I, O> q)
        {
            return new PersistentQueueDriver<>(this, q);
        }
    }

    private PersistentQueueDriver(Factory f, IPersistentQueue<I, O> q)
    {
        _f = f;
        _er = new ExponentialRetry(f._sched);
        _q = q;
    }

    /**
     * Add an entry to the tail of the queue
     *
     * NB: to allow calling from {@link com.aerofs.daemon.lib.db.ITransListener#committing_} this
     * method cannot add new transaction listeners, therefore {@link #scheduleScan_} should be
     * called manually from a {@link com.aerofs.daemon.lib.db.ITransListener#committed_} callback
     *
     * As much as possible you should use TransLocal to avoid adding multiple listeners per
     * transaction.
     */
    public void enqueue_(I payload, Trans t) throws SQLException
    {
        _q.enqueue_(payload, t);
    }

    /**
     * If {@link IPersistentQueue#process_} releases the core lock, it is conceivable that another
     * thread changes the global state in such a way that calling {@link IPersistentQueue#dequeue_}
     * would be incorrect, even though {@link IPersistentQueue#process_} returns true. In this case,
     * the thread in question should call this method to cause the scan to be restarted
     */
    public void restartScan_()
    {
        if (_scanInProgress) _retryScan = true;
    }

    /**
     * Schedule a scan of the queue
     *
     * Should be called on startup and every time a DB transaction affecting the queue is committed
     *
     * This methods garantees that no concurrent scan can happen
     *
     * NB: DelayedScheduler assumes that an ongoing event cannot do the job of an incoming event,
     * which is not true in this case. It also wouldn't do the exp-retry for us so it's not worth
     * trying to use it.
     */
    public void scheduleScan_(final Class<?>... excludes)
    {
        if (_scanInProgress) return;
        _f._sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                final long _seq = ++_scanSeq;
                _er.retry("pqscan", () -> {
                    if (_scanSeq != _seq) return null;
                    scanImpl_();
                    return null;
                }, excludes);
            }
        }, 0);
    }

    private void scanImpl_() throws Exception
    {
        if (_scanInProgress) return;
        _scanInProgress = true;
        try {
            O payload;
            while ((payload = _q.front_()) != null) {
                _retryScan = false;
                boolean ok = false;

                // pass down a Token to allow implementation to release and re-acquire core lock
                Token tk = _f._tokenManager.acquireThrows_(Cat.UNLIMITED, "pq-out");
                try {
                    ok = _q.process_(payload, tk);
                } finally {
                    tk.reclaim_();
                }

                if (_retryScan || !ok) continue;

                // remove successfully processed payload from persistent queue
                Trans t = _f._tm.begin_();
                try {
                    _q.dequeue_(payload, t);
                    t.commit_();
                } finally {
                    t.end_();
                }
            }
        } finally {
            _scanInProgress = false;
        }
    }
}
