/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import com.aerofs.base.C;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;

import java.util.Map;

/*
 * A class that's designed to throttle something based on the time of last occurence.
 *   The Throttler tracks the latest time when a value passes through the filter based on
 *   the key, and it will not let another value with the same key pass through the filter
 *   until at least _delay milliseconds have taken place.
 *
 * Constructed using the Builder pattern:
 *   - Delay: the minimum period of time, in ms, that should have elapsed before we allow
 *       another object with the same key to pass through the filter.
 *   - Throttler: the filter to determine which objects to throttle.
 *   - UntrackFilter: the filter to determine when to untrack a particular key.
 *
 * Logic:
 *   we track and throttle iff the throttle filter returns true and the time since last value
 *     is less than the event.
 *   we untrack iff the throttle filter returns false and the untrack filter returns true.
 *
 * Sample:
 *   // the following creates a Throttler with 1 second delay, applies to all values,
 *   //   and never untracks.
 *   Throttler throttle = Throttler.newBuilder()
 *       .setDelay(1000)
 *       .setThrottleFilter(Predicates.alwaysTrue())
 *       .setUntrackFilter(Predicates.alwaysFalse())
 *       .build();
 *
 *   if (throttle.shouldThrottle(key, value)) ; // throttle behaviour
 */
public class Throttler<TKey, TValue>
{
    private final Map<TKey, Long> _map;
    private final long _delay;
    private final Predicate<TValue> _fThrottle;
    private final Predicate<TValue> _fUntrack;

    private Throttler(long delay, Predicate<TValue> fThrottle, Predicate<TValue> fUntrack)
    {
        _map = Maps.newHashMap();
        _delay = delay;
        _fThrottle = fThrottle;
        _fUntrack = fUntrack;
    }

    public boolean shouldThrottle(TKey key, TValue value)
    {
        if (_fThrottle.apply(value)) {
            long now = System.currentTimeMillis();

            if (_map.containsKey(key)) {
                long then = _map.get(key);

                if (now - then < _delay) return true;
            }

            _map.put(key, now);

        } else if (_fUntrack.apply(value)) {
            _map.remove(key);
        }

        return false;
    }

    public static class Builder<TKey, TValue>
    {
        private long _delay                  = 1 * C.SEC;
        private Predicate<TValue> _fThrottle = Predicates.alwaysFalse();
        private Predicate<TValue> _fUntrack  = Predicates.alwaysFalse();

        public Builder<TKey, TValue> setDelay(long delay)
        {
            _delay = delay;
            return this;
        }

        public Builder<TKey, TValue> setThrottleFilter(Predicate<TValue> fThrottle)
        {
            _fThrottle = fThrottle;
            return this;
        }

        public Builder<TKey, TValue> setUntrackFilter(Predicate<TValue> fUntrack)
        {
            _fUntrack = fUntrack;
            return this;
        }

        public Throttler<TKey, TValue> build()
        {
            return new Throttler<TKey, TValue>(_delay, _fThrottle, _fUntrack);
        }
    }
}
