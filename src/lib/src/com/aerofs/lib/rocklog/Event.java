/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.rocklog;

public class Event extends RockLogMessage
{
    private static final String EVENT_NAME_KEY = "event_name";

    Event(RockLog rockLog, String eventName)
    {
        super(rockLog);

        addData(EVENT_NAME_KEY, eventName);
    }

    @Override
    String getURLPath()
    {
        return "/events";
    }
}
