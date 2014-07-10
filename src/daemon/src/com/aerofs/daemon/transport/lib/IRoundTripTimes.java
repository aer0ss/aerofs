/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import javax.annotation.Nullable;

public interface IRoundTripTimes
{
    /**
     * Add another data point for the channel @channelID. Call this when a heartbeat is returned over
     * a particular channel.
     */
    public void putMicros(int channelId, long time);

    /**
     * Get the "average" round trip time for the channel @channelId, or null if putMicros() has never been
     * called for that channel.
     *
     * The actual value is an exponential moving average of all values that have been added for the
     * channel being corrected.
     */
    public @Nullable Long getMicros(int channelId);
}
