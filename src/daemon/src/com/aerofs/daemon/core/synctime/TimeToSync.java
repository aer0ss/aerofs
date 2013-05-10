package com.aerofs.daemon.core.synctime;

import com.google.common.base.Objects;

/**
 * Encapsulates a time interval, and converts it to a histogram bin index
 */
class TimeToSync
{
    static final int TOTAL_BINS = 256;

    private final long _syncTimeMillis;

    TimeToSync(long syncTimeMillis)
    {
        _syncTimeMillis = syncTimeMillis;
    }

    /**
     * Linear bin distribution. Each bin represents 15 second increments.
     * One hour (3600 secs) sits at bin 240
     * @return timeInSeconds / 15
     *
     * N.B. previously a log bin axis was chosen with function 21.25 * log2(timeInSeconds), such
     * that 4096 seconds sat at bin 255. A negative consequence is that between bins 0 and 127,
     * seconds 0 to 60 are represented, which is far too much resolution for that time range.
     */
    int toBinIndex()
    {
        return Math.min((int) (_syncTimeMillis / (1000 * 15)), TOTAL_BINS - 1);
    }

    @Override
    public boolean equals(Object o)
    {
        return ((TimeToSync) o)._syncTimeMillis == _syncTimeMillis;
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(_syncTimeMillis);
    }

    @Override
    public String toString()
    {
        return Long.toString(_syncTimeMillis);
    }
}
