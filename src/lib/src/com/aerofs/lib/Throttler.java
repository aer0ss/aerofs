/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

/**
 * A class that's designed to throttle something based on the time of last occurence.
 *   The Throttler tracks the latest time when it was successfully applied to any given item,
 *   and it will only successfully apply to the same item if at least _delay milliseconds have
 *   past.
 *
 * Example:
 *   Throttler throttler = new Throttler(1 * C.SEC);
 *
 *   if (throttler.shouldThrottle(item)) ; // throttle behaviour
 */
public class Throttler<ThrottledItem>
{
    private final Map<ThrottledItem, Long> _map = newHashMap();
    private long _delay;

    public void setDelay(long delay)
    {
        _delay = delay;
    }

    public boolean shouldThrottle(ThrottledItem item)
    {
        long now = System.currentTimeMillis();

        if (_map.containsKey(item)) {
            long then = _map.get(item);
            if (now - then < _delay) return true;
        }

        _map.put(item, now);

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
