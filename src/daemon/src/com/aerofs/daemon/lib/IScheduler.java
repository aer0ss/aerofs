/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib;

import com.aerofs.daemon.event.IEvent;

/**
 * To be implemented by any classes that allow events to be scheduled for some
 * arbitrary time in the future.
 */
public interface IScheduler
{
    /**
     * Schedules a future event
     *
     * @param ev event to schedule
     * @param relativeTimeInMSec time (from now) after which the event will
     * be triggered. Given in ms
     */
    public void schedule(IEvent ev, long relativeTimeInMSec);
}
