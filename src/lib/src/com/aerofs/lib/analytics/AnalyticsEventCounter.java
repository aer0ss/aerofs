/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.analytics;

import com.aerofs.base.C;
import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.analytics.IAnalyticsEvent;
import com.aerofs.lib.DelayedRunner;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper class to count frequently occuring analytics events and send aggregate counts in a
 * separate thread.
 *
 * Note: We could move this class to base, but then we would have to move DelayedRunner too.
 */
public abstract class AnalyticsEventCounter
{
    // How long we wait to aggregate more data before sending the event
    private static final long AGGREGATE_INTERVAL = 5 * C.MIN;

    private final Analytics _analytics;
    private final DelayedRunner _runner;
    private final AtomicInteger _counter = new AtomicInteger(0);

    public AnalyticsEventCounter(Analytics analytics)
    {
        _analytics = analytics;
        _runner = new DelayedRunner("tix-evt", AGGREGATE_INTERVAL, new Runnable() {
            @Override
            public void run()
            {
                _analytics.track(createEvent(_counter.getAndSet(0)));
            }
        });
    }

    /**
     * Implement this method to return a new AnalyticsEvent with the proper count that should be
     * sent to the analytics backend
     */
    public abstract IAnalyticsEvent createEvent(int count);

    public void inc()
    {
        _counter.incrementAndGet();
        _runner.schedule();
    }
}
