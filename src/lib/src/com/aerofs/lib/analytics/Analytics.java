package com.aerofs.lib.analytics;

import java.util.concurrent.atomic.AtomicInteger;

import com.aerofs.base.C;
import com.aerofs.lib.DelayedRunner;
import com.aerofs.lib.rocklog.EventProperty;
import com.aerofs.lib.rocklog.EventType;
import com.aerofs.lib.rocklog.RockLog;

public class Analytics
{
    /**
     * Helper class to count frequently occuring events and send 1-minute-aggregate count to RockLog
     * in a separate thread.
     */
    private class RLCounter
    {
        private final DelayedRunner _runner;
        private final AtomicInteger _counter = new AtomicInteger(0);

        public RLCounter(String name, final EventType type)
        {
            _runner = new DelayedRunner(name, 60*C.SEC, new Runnable() {
                @Override
                public void run()
                {

                    RockLog.newEvent(type)
                            .addProperty(EventProperty.COUNT,
                                    Integer.toString(_counter.getAndSet(0)))
                            .sendAsync();
                }
            });
        }

        public void inc()
        {
            _counter.incrementAndGet();
            _runner.schedule();
        }
    }

    private final RLCounter _save = new RLCounter("analytics-save-file", EventType.FILE_SAVED);
    public void incSaveCount()
    {
        _save.inc();
    }

    private final RLCounter _conflict = new RLCounter("analytics-file-conflict",
                                                        EventType.FILE_CONFLICT);
    public void incConflictCount()
    {
        _conflict.inc();
    }
}
