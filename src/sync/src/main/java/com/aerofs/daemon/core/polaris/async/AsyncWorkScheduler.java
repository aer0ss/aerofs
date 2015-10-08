/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.async;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import org.slf4j.Logger;

import static com.google.common.base.Preconditions.checkState;

/**
 * A simple scheduler for asynchronous work. Shared by fetch and submit sides of Polaris
 * communication subsystem.
 *
 * Each instance of this class is responsible for scheduling a single recurring unit of work.
 * It enforces the following invariants:
 *   - no concurrent processing of the same task
 *   - continuous processing on success if more work is available
 *   - exponential retry on failure
 *   - multiple schedule requests are collapsed into a single task execution
 */
public class AsyncWorkScheduler implements AsyncTaskCallback
{
    private final static Logger l = Loggers.getLogger(AsyncWorkScheduler.class);

    /**
     * Valid states and transitions
     *   IDLE                   -> {IDLE, SCHEDULED, STOPPED}
     *   SCHEDULED              -> {IDLE, SCHEDULED, INFLIGHT, STOPPED}
     *   INFLIGHT               -> {IDLE, SCHEDULED, INFLIGHT|STOPPED}
     *   INFLIGHT|SCHEDULED     -> {      SCHEDULED, INFLIGHT|STOPPED}
     *   INFLIGHT|STOPPED       -> {INFLIGHT|SCHEDULED, STOPPED}
     *   STOPPED                -> {SCHEDULED}
     */
    private final int IDLE = 0;
    private final int SCHEDULED = 1;
    private final int INFLIGHT = 2;
    private final int STOPPED = 4;

    private final String _name;
    private final CoreScheduler _sched;
    private final AsyncTask _task;

    // synchronized on core lock
    private int _state;
    private long _delay;

    private CancellableEvent _ev = new CancellableEvent();

    private class CancellableEvent extends AbstractEBSelfHandling {
        private boolean _cancelled = false;

        void cancel_()
        {
            _cancelled = true;
        }

        @Override
        public void handle_()
        {
            if (_cancelled) return;
            checkState(_state == SCHEDULED);
            _state = INFLIGHT;
            _task.run_(AsyncWorkScheduler.this);
        }
    }

    public AsyncWorkScheduler(String name, CoreScheduler sched, AsyncTask task)
    {
        _name = name;
        _sched = sched;
        _task = task;
        _state = IDLE;
    }

    /**
     * Restart the scheduler
     *
     * no-op if the scheduler was already started
     */
    public void start_()
    {
        l.info("{} start", _name);
        _state &= ~STOPPED;
        schedule_();
    }

    /**
     * schedule work
     *
     * no-op if the scheduler is stopped
     * no-op if the task is already in the queue
     *
     * new task will be executed *after* the current one completes
     */
    public void schedule_()
    {
        if ((_state & STOPPED) != 0) {
            l.info("{} stopped", _name);
        } else if ((_state & INFLIGHT) != 0) {
            l.info("{} delayed resched {}", _name, _state);
            _state |= SCHEDULED;
        } else if ((_state & SCHEDULED) != 0) {
            if (_delay > 0) {
                l.info("{} fast resched {}", _name, _delay);
                _ev.cancel_();
                _ev = new CancellableEvent();
                _sched.schedule_(_ev);
            } else {
                l.debug("{} already sched", _name);
            }
        } else {
            checkState(_state == IDLE);
            l.info("{} imm sched", _name);
            _state = SCHEDULED;
            _sched.schedule_(_ev);
        }
    }

    /**
     * Stop the scheduler
     *
     * In-flight tasks will complete
     * Scheduled tasks will be be cancelled
     * No new task will be scheduled until the scheduler is restarted
     */
    public void stop_()
    {
        if ((_state & STOPPED) != 0) return;

        l.info("{} stop", _name);
        _delay = 0;
        _ev.cancel_();
        _ev = new CancellableEvent();
        _state &= ~SCHEDULED;
        _state |= STOPPED;
    }

    @Override
    public void onSuccess_(boolean hasMore)
    {
        checkState((_state & INFLIGHT) != 0);
        _state &= ~INFLIGHT;
        _delay = 0;
        if (_state == STOPPED) {
            l.info("{} stopping", _name);
            return;
        }
        if (hasMore || _state == SCHEDULED) {
            l.info("{} continue", _name);
            _state = SCHEDULED;
            // TODO: bypass core queue?
            _sched.schedule_(_ev);
        } else {
            l.info("{} done", _name);
            _state = IDLE;
        }
    }

    @Override
    public void onFailure_(Throwable t)
    {
        SystemUtil.fatalOnUncheckedException(t);
        checkState((_state & INFLIGHT) != 0);
        _state &= ~INFLIGHT;
        if (_state == STOPPED) {
            l.info("{} stopping {}", _name, BaseLogUtil.suppress(t, ExRetryLater.class));
            return;
        }
        if (_state == SCHEDULED) {
            l.info("{} fast retry", _name, BaseLogUtil.suppress(t, ExRetryLater.class));
            // TODO: bypass core queue?
            _sched.schedule_(_ev);
        } else {
            l.info("{} retry in {}", _name, _delay, BaseLogUtil.suppress(t, ExRetryLater.class));
            // exponential backoff
            _state = SCHEDULED;
            _delay = Math.min(LibParam.EXP_RETRY_MAX_DEFAULT,
                    Math.max(_delay * 2, LibParam.EXP_RETRY_MIN_DEFAULT));
            _sched.schedule(_ev, _delay);
        }
    }
}
