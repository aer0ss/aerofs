package com.aerofs.daemon.core.synctime;

class TimeToSync
{
    static final int TOTAL_BUCKETS = 256;

    private final long _syncTimeMillis;

    TimeToSync(long syncTimeMillis)
    {
        _syncTimeMillis = syncTimeMillis;
    }

    /**
     * @return 21.25 * log2(timeInSeconds)
     * 21.25 was chosen to place 4096 seconds in the top bin (255). 4096 is just over an hour.
     * 21.25 = 255 / log2(4096)
     */
    int toBucketIndex()
    {
        int syncTimeSeconds = (int) _syncTimeMillis / 1000;

        return Math.min(TOTAL_BUCKETS - 1,
                85 * (31 - Integer.numberOfLeadingZeros(syncTimeSeconds)) / 4);
    }

    @Override
    public boolean equals(Object o)
    {
        return ((TimeToSync) o)._syncTimeMillis == _syncTimeMillis;
    }
}
