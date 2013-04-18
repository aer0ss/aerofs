/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.analytics;

import java.util.Map;

/**
 * Interface for all analytics events
 */
public abstract interface IAnalyticsEvent
{
    /**
     * Name of the event
     */
    String getName();

    /**
     * Save event properties that will be sent to the analytics backend.
     *
     * All events have a set of default properties (see Analytics.java for a list), and may define
     * additional properties here.
     */
    void saveProperties(Map<String, String> properties);
}