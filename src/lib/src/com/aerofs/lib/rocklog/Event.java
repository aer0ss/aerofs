/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.rocklog;

import com.aerofs.lib.cfg.InjectableCfg;

public class Event extends RockLogMessage
{
    private static final String EVENT_NAME_KEY = "event_name";

    Event(RockLog rockLog, InjectableCfg cfg, EventType eventType)
    {
        super(rockLog, cfg);

        addData(EVENT_NAME_KEY, eventType.toString());
    }

    public Event addProperty(EventProperty prop, Object value)
    {
        addData(prop.toString(), value);

        return this;
    }

    @Override
    String getURLPath()
    {
        return "/events";
    }
}
