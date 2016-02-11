/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.async;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.transport.lib.exceptions.ExDeviceUnavailable;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A simple scheduler for a group of independent asynchronous tasks that share underlying
 * resources.
 *
 * For any given task in the group, it enforces the following invariants:
 *   - no concurrent processing of the same task
 *   - continuous processing on success if more work is available
 *   - exponential retry on failure
 *   - multiple schedule requests are collapsed into a single task execution
 *
 * In addition, it never puts more than one event in the core queue, regardless of the number
 * of tasks in the group.
 */
public class AsyncWorkGroupScheduler extends AbstractEBSelfHandling
{
    private final static Logger l = Loggers.getLogger(AsyncWorkGroupScheduler.class);

    /**
     * Valid task states and transitions
     *   IDLE                   -> {IDLE, SCHEDULED, STOPPED}
     *   SCHEDULED              -> {IDLE, SCHEDULED, INFLIGHT, STOPPED}
     *   INFLIGHT               -> {IDLE, SCHEDULED, INFLIGHT|STOPPED}
     *   INFLIGHT|SCHEDULED     -> {      SCHEDULED, INFLIGHT|STOPPED}
     *   INFLIGHT|STOPPED       -> {INFLIGHT|SCHEDULED, STOPPED}
     *   STOPPED                -> {SCHEDULED}
     */
    private static final int IDLE = 0;
    private static final int SCHEDULED = 1;
    private static final int INFLIGHT = 2;
    private static final int STOPPED = 4;

    private final CoreScheduler _sched;

    // active task set (only modified in handle_)
    private int _idx;
    private List<TaskState> _active = new ArrayList<>();

    // future task set, synchronized on core lock
    private final Set<TaskState> _scheduled = new LinkedHashSet<>();
    private final SortedSet<TaskState> _delayed = new TreeSet<>();

    private final ElapsedTimer _origin = new ElapsedTimer();

    // next scheduled insertion of event in the core queue, cancellable
    private long _schedD;
    // non-null if an event is scheduled
    // true if the event is "done", i.e. past the point where the blocking enqueue can be cancelled
    private AtomicBoolean _schedF;

    @Override
    public void handle_()
    {
        try {
            // process active task set incrementally
            while (_idx < _active.size()) {
                TaskState ts = _active.get(_idx++);
                if ((ts._state & STOPPED) != 0) continue;

                checkState(ts._state == SCHEDULED);
                ts._state = INFLIGHT;
                ts._t.run_(ts);
                // TODO: determine room for pipelining
                // TODO: figure out how many subsequent tasks to process in one go
                return;
            }

            // check for ripe delayed tasks
            long now = _origin.nanosElapsed();
            Iterator<TaskState> it = _delayed.iterator();
            while (it.hasNext()) {
                TaskState ts = it.next();
                checkState(ts._timeout > 0);
                long next = MILLISECONDS.convert(ts._timeout - now, NANOSECONDS);
                if (next > 50) {
                    l.debug("next unripe {} {}ms", ts._name, next);
                    break;
                }
                l.debug("ripe delayed task {}", ts._name);
                it.remove();
                ts._timeout = 0;
                _scheduled.add(ts);
            }

            // swap task sets
            _active.clear();
            _active.addAll(_scheduled);
            _scheduled.clear();
            _idx = 0;
        } finally {
            // reschedule self for further task processing if needed
            if (_active.isEmpty()) {
                TaskState ts = Iterables.getFirst(_delayed, null);
                if (ts != null) {
                    l.debug("next up {}", ts._name);
                    reschedWithDelay_(ts._timeout);
                } else if (_schedF != null) {
                    l.debug("cancel task processing");
                    _schedF.set(true);
                    _schedF = null;
                } else {
                    l.debug("done processing tasks");
                }
            } else {
                reschedNow_();
            }
        }
    }

    @Inject
    public AsyncWorkGroupScheduler(CoreScheduler sched) {
        _sched = sched;
    }

    public TaskState register_(String name, AsyncTask task) {
        return new TaskState(name, task);
    }

    private void reschedNow_() {
        _sched.schedule_(this);
    }

    private void reschedWithDelay_(long timeout) {
        long base = _origin.nanosElapsed();
        long req = MILLISECONDS.convert(Math.max(0L, timeout - base), NANOSECONDS);
        long cur = MILLISECONDS.convert(Math.max(0L, _schedD - base), NANOSECONDS);
        // cancel any existing future that's too far away
        // TODO: refine cancellation policy
        if (_schedF != null && cur > req + 50) {
            _schedF.set(true);
            _schedF = null;
        }
        if (_schedF == null || _schedF.get()) {
            l.debug("restart task processing in {}ms", req);
            _schedD = timeout;
            _schedF = _sched.scheduleCancellable(this, req);
        } else {
            l.debug("req redundant task processing in {}ms [{}ms]", req, cur);
        }
    }

    public class TaskState implements Comparable<TaskState>, AsyncTaskCallback {
        private int _state = STOPPED;
        private long _delay = 0;
        private long _timeout = 0;
        private final String _name;
        private final AsyncTask _t;

        TaskState(String name, AsyncTask t) {
            _name = name;
            _t = t;
        }

        /**
         * Restart the scheduler
         *
         * no-op if the scheduler was already started
         */
        public void start_() {
            if ((_state & STOPPED) == 0) return;
            l.info("{} start", _name);
            _state &= ~STOPPED;
            checkState((_state & SCHEDULED) == 0);
            schedule_();
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
            _state |= STOPPED;
            _state &= ~SCHEDULED;
            _scheduled.remove(this);
            if (_timeout > 0) {
                _delayed.remove(this);
                _timeout = 0;
                if (_delayed.isEmpty() && _schedF != null) {
                    _schedF.set(true);
                    _schedF = null;
                }
            }
            _delay = 0;
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
            } else if (_state == SCHEDULED) {
                if (_timeout > 0) {
                    l.info("{} fast resched {}", _name, _delay);
                    _delayed.remove(this);
                    _timeout = 0;
                    activate_();
                } else {
                    l.trace("{} already sched", _name);
                }
            } else {
                checkState(_state == IDLE);
                l.info("{} imm sched", _name);
                _state = SCHEDULED;
                activate_();
            }
        }

        private void activate_() {
            // resume task processing if needed
            if (_active.isEmpty() && _scheduled.isEmpty()) {
                reschedNow_();
            }
            _scheduled.add(this);
        }

        @Override
        public int compareTo(TaskState o) {
            if (o == this) return 0;
            int d = Long.compare(_timeout, o._timeout);
            if (d == 0) d = hashCode() - o.hashCode();
            if (d == 0) d = _name.compareTo(o._name);
            checkState(d != 0);
            return d;
        }

        @Override
        public void onSuccess_(boolean hasMore) {
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
                activate_();
            } else {
                l.info("{} done", _name);
                _state = IDLE;
            }
        }

        private Throwable suppress(Throwable t) {
            return BaseLogUtil.suppress(t, ExRetryLater.class, SocketException.class,
                    ExNoPerm.class, ExDeviceUnavailable.class, ClosedChannelException.class,
                    UnresolvedAddressException.class);
        }

        @Override
        public void onFailure_(Throwable t) {
            SystemUtil.fatalOnUncheckedException(t);
            checkState((_state & INFLIGHT) != 0);
            _state &= ~INFLIGHT;
            if (_state == STOPPED) {
                l.info("{} stopping {}", _name, suppress(t));
                return;
            }
            if (_state == SCHEDULED) {
                l.info("{} fast retry", _name, suppress(t));
                activate_();
            } else {
                // exponential backoff
                _state = SCHEDULED;
                if (_timeout > 0) {
                    _delayed.remove(this);
                }
                _delay = Math.min(LibParam.EXP_RETRY_MAX_DEFAULT, Math.max(_delay * 2, 250));
                l.info("{} retry in {}", _name, _delay, suppress(t));
                if (_delayed.isEmpty()) {
                    if (_schedF != null) {
                        _schedF.set(true);
                        _schedF = null;
                    }
                    _origin.restart();
                }
                _timeout = _origin.nanosElapsed() + NANOSECONDS.convert(_delay, MILLISECONDS);
                checkState(_timeout > 0);
                _delayed.add(this);
                if (_active.isEmpty() && _scheduled.isEmpty()) {
                    reschedWithDelay_(_timeout);
                }
            }
        }
    }
}
