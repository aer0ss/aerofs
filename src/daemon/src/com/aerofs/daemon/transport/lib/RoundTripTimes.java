/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Map;

public class RoundTripTimes implements IRoundTripTimes
{
    private static Logger l = Loggers.getLogger(RoundTripTimes.class);
    private static float _weight = 0.7f;  // for use in exponentially weighted average algorithm

    private final Map<Integer, Long> _map = Maps.newTreeMap();

    /**
     * N.B. this is not synchronized. If this is called concurrently for the same channelId, one
     * update could be lost. We're ok with this.
     */
    @Override
    public void putMicros(int channelId, long time)
    {
        l.trace("rtt putMicros {} {}", channelId, time);
        if (!_map.containsKey(channelId)) {
            _map.put(channelId, time);
            return;
        }
        long rttOld = _map.get(channelId);
        long rttNew = (long)((_weight * (float)rttOld) + ((1 - _weight) * (float)time));
        _map.put(channelId, rttNew);
    }

    @Override
    public @Nullable Long getMicros(int channelId)
    {
        return _map.get(channelId);
    }
}
