/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.lib;

import com.aerofs.base.Loggers;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.Util;
import org.slf4j.Logger;

import java.util.concurrent.Callable;

/**
 * Melds ExponentialRetry and DelayedScheduler
 *   - at most one event being processed at any given time
 *   - at most one event being scheduled at any given time
 *   - failing events are automatically retried (w/ exponential backoff)
 *   - new scheduled event resets the retry delay if exp retry ongoing
 */
public class ExpRetryScheduler
{
    private final Logger l = Loggers.getLogger(ExpRetryScheduler.class);

    private final String _name;
    private final Scheduler _sched;
    private final Callable<Void> _activity;
    private final Class<?>[] _excludes;
    private boolean _scheduled;
    private boolean _ongoing;

    public ExpRetryScheduler(Scheduler sched, String name, Callable<Void> activity,
            Class<?>... excludes)
    {
        _name = name;
        _sched = sched;
        _activity = activity;
        _excludes = excludes;
    }

    public void schedule_()
    {
        if (_scheduled) return;
        _scheduled = true;
        if (_ongoing) return;

        _sched.schedule(new AbstractEBSelfHandling() {
            private long _delay = LibParam.EXP_RETRY_MIN_DEFAULT;
            @Override
            public void handle_()
            {
                _scheduled = false;
                _ongoing = true;
                long rescheduleDelay = 0;
                try {
                    _activity.call();
                    // reset exp-retry delay on successful call
                    _delay = LibParam.EXP_RETRY_MIN_DEFAULT;
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    rescheduleDelay = _scheduled ? LibParam.EXP_RETRY_MIN_DEFAULT : _delay;
                    _delay = Math.min(_delay * 2, LibParam.EXP_RETRY_MAX_DEFAULT);
                    _scheduled = true;
                    l.warn(_name + " failed. exp-retry in " + rescheduleDelay + ": " +
                            Util.e(e, _excludes));
                } finally {
                    _ongoing = false;
                }

                if (_scheduled) _sched.schedule(this, rescheduleDelay);
            }
        }, 0);
    }
}