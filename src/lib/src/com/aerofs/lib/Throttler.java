/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import com.google.common.collect.Maps;

import java.util.Map;

/*
 * A class that's designed to throttle something based on the time of last occurence.
 *   The Throttler tracks the latest time when it was successfully applied to any given key,
 *   and it will only successfully apply to the same key if at least _delay milliseconds have
 *   past.
 *
 * Example:
 *   Throttler throttler = new Throttler(1 * C.SEC);
 *
 *   if (throttler.shouldThrottle(key)) ; // throttle behaviour
 */
public class Throttler<TKey>
{
    private final Map<TKey, Long> _map;
    private final long _delay;

    public Throttler(long delay)
    {
        _map = Maps.newHashMap();
        _delay = delay;
    }

    public boolean shouldThrottle(TKey key)
    {
        long now = System.currentTimeMillis();

        if (_map.containsKey(key)) {
            long then = _map.get(key);

            if (now - then < _delay) return true;
        }

        return false;
    }

    public void untrack(TKey key)
    {
        _map.remove(key);
    }
}
