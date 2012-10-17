package com.aerofs.lib.analytics;

import java.util.concurrent.atomic.AtomicInteger;

import com.aerofs.lib.C;
import com.aerofs.lib.DelayedRunner;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.proto.Sv.PBSVEvent.Type;

public class Analytics
{
    /**
     * Helper class to count frequently occuring events and send 1-minute-aggregate count to SV in
     * a separate thread.
     */
    private class SVCounter
    {
        private final DelayedRunner _runner;
        private final AtomicInteger _counter = new AtomicInteger(0);

        public SVCounter(String name, final Type t)
        {
            _runner = new DelayedRunner(name, 60*C.SEC, new Runnable() {
                @Override
                public void run()
                {
                    SVClient.sendEventAsync(t, Integer.toString(_counter.getAndSet(0)));
                }
            });
        }

        public void inc()
        {
            _counter.incrementAndGet();
            _runner.schedule();
        }
    }

    private final SVCounter _save = new SVCounter("analytics-save-file", Type.FILE_SAVED);
    public void incSaveCount()
    {
        _save.inc();
    }

    private final SVCounter _conflict = new SVCounter("analytics-file-conflict", Type.FILE_CONFLICT);
    public void incConflictCount()
    {
        _conflict.inc();
    }
}
