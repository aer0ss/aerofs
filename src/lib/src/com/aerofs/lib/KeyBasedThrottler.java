/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import com.google.common.collect.Maps;

import java.util.Map;

/*
 * A class that's designed to throttle something based on the time of last occurence.
 *   The KeyBasedThrottler tracks the latest time when it was successfully applied to any given key,
 *   and it will only successfully apply to the same key if at least _delay milliseconds have
 *   past.
 *
 * Example:
 *   KeyBasedThrottler throttler = new KeyBasedThrottler(1 * C.SEC);
 *
 *   if (throttler.shouldThrottle(key)) ; // throttle behaviour
 */
public class KeyBasedThrottler<TKey>
{
    private final Map<TKey, Long> _map;
    private long _delay;

    public KeyBasedThrottler()
    {
        _map = Maps.newHashMap();
    }

    public void setDelay(long delay)
    {
        _delay = delay;
    }

    public boolean shouldThrottle(TKey key)
    {
        long now = System.currentTimeMillis();

        if (_map.containsKey(key)) {
            long then = _map.get(key);
            if (now - then < _delay) return true;
        }

        _map.put(key, now);

        return false;
    }

    public void untrack(TKey key)
    {
        _map.remove(key);
    }

    public void clear()
    {
        _map.clear();
    }

    public static class Factory
    {
        public <TKey> KeyBasedThrottler<TKey> create()
        {
            return new KeyBasedThrottler<TKey>();
        }
    }
}
