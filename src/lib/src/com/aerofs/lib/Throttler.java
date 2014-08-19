/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import com.aerofs.base.ElapsedTimer;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

/**
 * A class that's designed to throttle something based on the time of last occurence.
 *   The Throttler tracks how long has passed since the last time when it was successfully applied
 *   to any given item, and it will only successfully apply to the same item if at least _delay
 *   milliseconds have passed.
 *
 * Example:
 *   Throttler throttler = new Throttler(1 * C.SEC);
 *
 *   if (throttler.shouldThrottle(item)) ; // throttle behaviour
 */
public class Throttler<ThrottledItem>
{
    private final ElapsedTimer.Factory _factTimer;

    private final Map<ThrottledItem, ElapsedTimer> _map = newHashMap();
    private long _delay;

    public Throttler(ElapsedTimer.Factory factTimer)
    {
        _factTimer = factTimer;
    }

    public void setDelay(long delay)
    {
        _delay = delay;
    }

    public boolean shouldThrottle(ThrottledItem item)
    {
        if (_map.containsKey(item)) {
            if (_map.get(item).elapsed() < _delay) return true;
        }

        ElapsedTimer timer = _factTimer.create();
        _map.put(item, timer);

        return false;
    }

    public void untrack(ThrottledItem item)
    {
        _map.remove(item);
    }

    public void clear()
    {
        _map.clear();
    }
}
