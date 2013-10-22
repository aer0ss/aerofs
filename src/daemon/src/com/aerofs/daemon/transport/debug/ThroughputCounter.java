/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.debug;


import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import org.slf4j.Logger;

/**
 * Simple class to measure the throughput of a system
 */
public final class ThroughputCounter
{
    private static final Logger l = Loggers.getLogger(ThroughputCounter.class);
    private static final int SAMPLE_SIZE = 1000;

    private final String counterName;
    private long sampleStartTime;
    private long totalBytesObserved;
    private int sampleCount;

    /**
     * @param counterName: string that will be displayed in the logs
     */
    public ThroughputCounter(String counterName)
    {
        this.counterName = counterName;
    }

    /**
     * This method is not thread safe.
     */
    public void observe(long bytes)
    {
        if (sampleStartTime == 0) {
            sampleStartTime = System.nanoTime();
            return;
        }

        totalBytesObserved += bytes;
        sampleCount++;

        if (sampleCount >= SAMPLE_SIZE) {
            long now = System.nanoTime();

            long totalTime = (now - sampleStartTime) / 1000000; // milliseconds
            l.info("##### TPUT {}: {} MB/s", counterName,  ((float) totalBytesObserved / totalTime) / ((float) C.MB / C.SEC));

            totalBytesObserved = 0;
            sampleStartTime = now;
            sampleCount = 0;
        }
    }
}
