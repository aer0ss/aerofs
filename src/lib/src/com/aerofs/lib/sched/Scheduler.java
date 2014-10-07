/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.sched;

import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

public class Scheduler implements IScheduler
{
    private final IBlockingPrioritizedEventSink<IEvent> _sink;
    private final String _name;

    private ScheduledExecutorService _executor;

    // @param sink  the queue timed out events will be sent to
    // @param name  the name of the scheduler thread
    public Scheduler(IBlockingPrioritizedEventSink<IEvent> sink, String name)
    {
        _sink = checkNotNull(sink);
        _name = checkNotNull(name);
        _executor = Executors.newScheduledThreadPool(1, r -> new Thread(r, _name));
    }

    public void shutdown()
    {
        _executor.shutdownNow();
    }

    @Override
    public void schedule(final IEvent ev, long relativeTimeInMSec)
    {
        _executor.schedule(() -> _sink.enqueueBlocking(ev, Prio.LO),
                relativeTimeInMSec, TimeUnit.MILLISECONDS);
    }
}
