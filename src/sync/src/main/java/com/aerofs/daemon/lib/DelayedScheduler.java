package com.aerofs.daemon.lib;

import com.aerofs.lib.sched.Scheduler;
import com.aerofs.lib.event.AbstractEBSelfHandling;

// schedule core activities by a delay. scheduling during the activity schedules
// the activity again after the same delay.
//
// two advantages of delayed scheduling are:
// * complete the task which schedules the activity as soon as possible. this
//   is useful for heavy activities as file system scan
// * batch multiple requests, fulfill them by a single run of the activity
//
public class DelayedScheduler {

    private final Scheduler _sched;
    private final Runnable _activity;
    private final long _delay;
    private boolean _scheduled;
    private boolean _ongoing;

    public DelayedScheduler(Scheduler sched, long delay, Runnable activity)
    {
        _sched = sched;
        _activity = activity;
        _delay = delay;
    }

    public void schedule_()
    {
        schedule_(_delay);
    }

    /**
     * schedule with a specified delay. the request is ignored if the activity
     * is already scheduled.
     */
    public void schedule_(long delay)
    {
        if (_scheduled) return;
        _scheduled = true;
        if (_ongoing) return;

        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                _scheduled = false;
                _ongoing = true;
                _activity.run();
                _ongoing = false;

                if (_scheduled) _sched.schedule(this, _delay);
            }
        }, delay);
    }

}
