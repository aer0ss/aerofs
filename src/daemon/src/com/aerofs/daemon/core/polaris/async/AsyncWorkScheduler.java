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

    private static enum State
    {
        IDLE,
        SCHEDULED,
        INFLIGHT,
        STOPPING
    }

    private final String _name;
    private final CoreScheduler _sched;
    private final AsyncTask _task;

    // synchronized on core lock
    private State _state;
    private long _delay;

    private final AbstractEBSelfHandling _ev = new AbstractEBSelfHandling() {
        @Override
        public void handle_()
        {
            if (_state == State.STOPPING) {
                l.info("{} stopping", _name);
                _state = State.IDLE;
                return;
            }
            checkState(_state == State.SCHEDULED);
            _state = State.INFLIGHT;
            _task.run_(AsyncWorkScheduler.this);
        }
    };

    public AsyncWorkScheduler(String name, CoreScheduler sched, AsyncTask task)
    {
        _name = name;
        _sched = sched;
        _task = task;
        _state = State.IDLE;
    }

    /**
     * NB: no-op if a task is already being executed
     *
     * This relies on the task correctly detecting and reporting that extra work
     * is available when it re-acquires the core lock.
     */
    public void schedule_()
    {
        if (_state != State.IDLE) return;
        _state = State.SCHEDULED;
        _sched.schedule(_ev, 0);
    }

    public void stop_()
    {
        if (_state == State.IDLE) return;
        _state = State.STOPPING;
    }

    @Override
    public void onSuccess_(boolean hasMore)
    {
        if (_state == State.STOPPING) {
            l.info("{} stopping", _name);
            _state = State.IDLE;
            return;
        }
        checkState(_state == State.INFLIGHT);
        _delay = 0;
        if (hasMore) {
            l.info("{} continue", _name);
            _state = State.SCHEDULED;
            _sched.schedule(_ev, _delay);
        } else {
            l.info("{} done", _name);
            _state = State.IDLE;
        }
    }

    @Override
    public void onFailure_(Throwable t)
    {
        checkState(_state == State.INFLIGHT);
        SystemUtil.fatalOnUncheckedException(t);
        // exponential backoff
        _delay = Math.min(LibParam.EXP_RETRY_MAX_DEFAULT,
                Math.max(_delay * 2, LibParam.EXP_RETRY_MIN_DEFAULT));
        l.info("{} retry in {}", _name, _delay, BaseLogUtil.suppress(t));
        _state = State.SCHEDULED;
        _sched.schedule(_ev, _delay);
    }
}
