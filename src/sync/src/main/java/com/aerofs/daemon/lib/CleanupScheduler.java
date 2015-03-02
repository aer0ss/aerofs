/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.lib;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.sched.Scheduler;
import org.slf4j.Logger;

import static com.google.common.base.Preconditions.checkState;

/**
 * Shared scheduling logic between logical and physical staging area
 *
 * This scheduler is guaranteed to be in one of three possible states:
 *   - IDLE         0 event in core queue, 0 event being processed
 *   - SCHEDULED    1 event in core queue, 0 event being processed
 *   - PROCESSING   0 event in core queue, 1 event being processed
 *
 * If the underlying processor fails, the scheduler will keep retrying
 * with exponential backoff.
 *
 * For slightly different takes on event scheduling, see also:
 * {@link com.aerofs.lib.sched.ExponentialRetry}
 * {@link com.aerofs.daemon.lib.DelayedScheduler}
 */
public class CleanupScheduler
{
    private final static Logger l = Loggers.getLogger(CleanupScheduler.class);

    public static interface CleanupHandler
    {
        public String name();

        /**
         * Perform cleanup for zero or more items
         *
         * Called from a core thread with the core lock held.
         *
         * The processor should release the core lock around long-running operations if possible
         * or at the very least attempt to process entries in small enough batches that it doesn't
         * starve other core threads.
         *
         * @return true iff the staging area contains more items to process
         */
        public boolean process_() throws Exception;
    }

    private static enum State
    {
        IDLE,
        SCHEDULED,
        PROCESSING
    }

    private final Scheduler _sched;
    private final CleanupHandler _processor;

    private final AbstractEBSelfHandling _ev = new AbstractEBSelfHandling() {
        @Override
        public void handle_()
        {
            processAndRescheduleAsNeeded_();
            checkState(_state != State.PROCESSING);
        }
    };

    // sync on core lock
    private State _state;
    private long _delay;

    public CleanupScheduler(CleanupHandler proc, CoreScheduler sched)
    {
        _processor = proc;
        _sched = sched;
        _state = State.IDLE;
    }

    public void schedule_()
    {
        if (_state != State.IDLE) return;
        _state = State.SCHEDULED;
        _sched.schedule(_ev, 0);
    }

    private void processAndRescheduleAsNeeded_()
    {
        checkState(_state == State.SCHEDULED);
        _state = State.PROCESSING;
        l.debug("{} processing", _processor.name());
        try {
            boolean hasMore = _processor.process_();
            // reset backoff interval on success
            _delay = 0;
            // do not reschedule if the staging area is empty
            if (!hasMore) {
                l.debug("{} done", _processor.name());
                _state = State.IDLE;
                return;
            }
        } catch (Exception e) {
            SystemUtil.fatalOnUncheckedException(e);
            // exponential backoff
            _delay = Math.min(LibParam.EXP_RETRY_MAX_DEFAULT,
                    Math.max(_delay * 2, LibParam.EXP_RETRY_MIN_DEFAULT));
            l.info("cleanup failed, retry in {}", _delay, BaseLogUtil.suppress(e));
        }
        l.debug("{} resched in {}", _processor.name(), _delay);
        _state = State.SCHEDULED;
        _sched.schedule(_ev, _delay);
    }
}
