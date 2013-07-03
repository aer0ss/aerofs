/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base;

/**
 * A timer for measuring time deltas.  Use this whenever you need a measurement of time elapsed,
 * rather than an absolute calendar time.
 * Unlike System.currentTimeMillis(), this class uses your platforms monotonic timers, so it won't
 * screw things up when you hit any of the weirdnesses that affect wall clock timers:
 *   - user changed the hardware clock
 *   - NTP daemon changed system clock
 *   - virtualization solution has clock off by >24 hours
 *   - a leap second rolled Unix time backwards one second
 *   - etc.
 * This class is suitable for internal performance measurements - see nanosElapsed().
 */
public class ElapsedTimer
{
    // A reference timestamp that is not associated with any particular absolute time.
    private long _startTimeNanos;

    /**
     * Sets the reference time.
     */
    public void start()
    {
        _startTimeNanos = System.nanoTime();
    }

    /**
     * Returns the number of milliseconds that have passed since the reference time was set.
     */
    public long elapsed()
    {
        return (System.nanoTime() - _startTimeNanos) / C.NSEC_PER_MSEC;
    }

    /**
     * Returns the number of nanoseconds that have passed since the reference time was set.
     */
    public long nanosElapsed()
    {
        return System.nanoTime() - _startTimeNanos;
    }

    /**
     * Sets the reference time and returns the amount of time between now and the previous time the
     * reference time was set.  This is largely an optimization to avoid reading the monotonic
     * clock twice, as well as to avoid losing track of nanoseconds between the read and update.
     */
    public long restart()
    {
        long start = _startTimeNanos;
        _startTimeNanos = System.nanoTime();
        return _startTimeNanos - start;
    }

    /**
     * Returns the number of milliseconds between reference times for two timers.
     * If other was started before this object, this function should return a positive value.
     * If other was started after this object, this function should return a negative value.
     *
     * Sometimes it is useful to compare two timers, to see the difference between when they were
     * started.  This can be helpful for computing differences between timeouts without needing
     * an absolute reference time.
     */
    public long msecsTo(ElapsedTimer other)
    {
        return (_startTimeNanos - other._startTimeNanos) / C.NSEC_PER_MSEC;
    }

}
